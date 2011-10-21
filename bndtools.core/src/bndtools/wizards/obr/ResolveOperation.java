package bndtools.wizards.obr;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.felix.bundlerepository.Capability;
import org.apache.felix.bundlerepository.DataModelHelper;
import org.apache.felix.bundlerepository.Requirement;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.impl.DataModelHelperImpl;
import org.apache.felix.bundlerepository.impl.RepositoryAdminImpl;
import org.apache.felix.utils.log.Logger;
import org.bndtools.core.utils.filters.ObrConstants;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;

import aQute.bnd.build.Container;
import aQute.bnd.build.Container.TYPE;
import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.service.OBRIndexProvider;
import aQute.bnd.service.OBRResolutionMode;
import aQute.bnd.service.RepositoryPlugin;
import aQute.bnd.service.RepositoryPlugin.Strategy;
import aQute.libg.header.OSGiHeader;
import aQute.libg.version.Version;
import bndtools.BndConstants;
import bndtools.Central;
import bndtools.Plugin;
import bndtools.api.EE;
import bndtools.api.IBndModel;
import bndtools.model.clauses.VersionedClause;

public class ResolveOperation implements IRunnableWithProgress {

    private final DataModelHelper helper = new DataModelHelperImpl();

    private final IFile runFile;
    private final IBndModel model;

    private final MultiStatus status;

    private ObrResolutionResult result = null;

    public ResolveOperation(IFile runFile, IBndModel model, MultiStatus status) {
        this.runFile = runFile;
        this.model = model;
        this.status = status;
    }

    public void run(IProgressMonitor monitor) {
        SubMonitor progress = SubMonitor.convert(monitor, "Resolving...", 0);

        // Get the repositories
        List<OBRIndexProvider> repos;
        try {
            repos = loadRepos();
        } catch (Exception e) {
            status.add(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error loading OBR indexes.", e));
            return;
        }

        // Create the dummy system bundle and repository admin
        File frameworkFile = findFramework();
        if (frameworkFile == null)
            return;
        DummyBundleContext bundleContext;
        try {
            bundleContext = new DummyBundleContext(frameworkFile);
        } catch (IOException e) {
            status.add(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error reading system bundle manifest.", e));
            return;
        }
        RepositoryAdminImpl repoAdmin = new RepositoryAdminImpl(bundleContext, new Logger(Plugin.getDefault().getBundleContext()));

        // Populate repository URLs
        for (OBRIndexProvider repo : repos) {
            String repoName = (repo instanceof RepositoryPlugin) ? ((RepositoryPlugin) repo).getName() : repo.toString();
            try {
                for (URL indexUrl : repo.getOBRIndexes()) {
                    repoAdmin.addRepository(indexUrl);
                }
            } catch (Exception e) {
                status.add(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error processing index for repository " + repoName, e));
            }
        }

        Resolver resolver = repoAdmin.resolver();

        // Add EE capabilities
        EE ee = model.getEE();
        if (ee == null)
            ee = EE.J2SE_1_5; // TODO: read default from the workbench
        resolver.addGlobalCapability(createEeCapability(ee));
        for (EE compat : ee.getCompatible()) {
            resolver.addGlobalCapability(createEeCapability(compat));
        }

        // Add requirements
        List<Requirement> requirements = model.getRunRequire();
        if (requirements != null) for (Requirement req : requirements) {
            resolver.add(req);
        }

        boolean resolved = resolver.resolve();

        result = new ObrResolutionResult(resolved, Arrays.asList(resolver.getRequiredResources()), Arrays.asList(resolver.getOptionalResources()),
                Arrays.asList(resolver.getUnsatisfiedRequirements()));
    }

    public ObrResolutionResult getResult() {
        return result;
    }

    private List<OBRIndexProvider> loadRepos() throws Exception {
        List<OBRIndexProvider> plugins = Central.getWorkspace().getPlugins(OBRIndexProvider.class);
        List<OBRIndexProvider> repos = new ArrayList<OBRIndexProvider>(plugins.size());

        for (OBRIndexProvider plugin : plugins) {
            if (plugin.getSupportedModes().contains(OBRResolutionMode.runtime))
                repos.add(plugin);
        }

        return repos;
    }

    private File findFramework() {
        String runFramework = model.getRunFramework();
        Map<String, Map<String, String>> header = OSGiHeader.parseHeader(runFramework);
        if (header.size() != 1) {
            status.add(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Invalid format for " + BndConstants.RUNFRAMEWORK + " header", null));
            return null;
        }

        Entry<String, Map<String, String>> entry = header.entrySet().iterator().next();
        VersionedClause clause = new VersionedClause(entry.getKey(), entry.getValue());

        String versionRange = clause.getVersionRange();
        if (versionRange == null)
            versionRange = new Version(0, 0, 0).toString();

        try {
            Container container = getProject().getBundle(clause.getName(), versionRange, Strategy.HIGHEST, null);
            if (container.getType() == TYPE.ERROR) {
                status.add(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Unable to find specified OSGi framework: " + container.getError(), null));
                return null;
            }
            return container.getFile();
        } catch (Exception e) {
            status.add(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error while trying to find the specified OSGi framework.", e));
            return null;
        }
    }

    private Project getProject() throws Exception {
        File file = runFile.getLocation().toFile();

        Project result;
        if ("bndrun".equals(runFile.getFileExtension())) {
            result = new Project(Central.getWorkspace(), file.getParentFile(), file);

            File bndbnd = new File(file.getParentFile(), Project.BNDFILE);
            if (bndbnd.isFile()) {
                Project parentProject = Workspace.getProject(file.getParentFile());
                result.setParent(parentProject);
            }
        } else if (Project.BNDFILE.equals(runFile.getName())) {
            result = Workspace.getProject(file.getParentFile());
        } else {
            throw new Exception("Invalid run file: " + runFile.getLocation());
        }
        return result;
    }

    private Capability createEeCapability(EE ee) {
        Map<String, String> props = new HashMap<String, String>();
        props.put(ObrConstants.FILTER_EE, ee.getEEName());

        return helper.capability(ObrConstants.REQUIREMENT_EE, props);
    }

}