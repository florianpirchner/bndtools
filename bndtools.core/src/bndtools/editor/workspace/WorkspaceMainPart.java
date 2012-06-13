package bndtools.editor.workspace;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.SectionPart;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ImageHyperlink;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import bndtools.Central;
import bndtools.Plugin;

public class WorkspaceMainPart extends SectionPart {

    private final boolean mainFile;

    private final Color warningColor;
    private final Image bndFileImg = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "icons/bndtools-logo-16x16.png").createImage();
    private final Image extFileImg = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "icons/bullet_go.png").createImage();
    private final Image warningImg = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "icons/warning_obj.gif").createImage();

    public WorkspaceMainPart(boolean mainFile, Composite parent, FormToolkit toolkit, int style) {
        super(parent, toolkit, style);
        this.mainFile = mainFile;

        warningColor = new Color(parent.getDisplay(), 255, 215, 210);
        createSection(getSection(), toolkit);
    }

    private void createSection(Section section, FormToolkit toolkit) {
        section.setText("Workspace");
        if (mainFile) {
            section.setDescription("This file is used to define your specific settings. Default settings are defined in the files linked below.");
        } else {
            section.setDescription("This file is part of the default bnd workspace settings. It should not be edited, instead define overrides in the main build.bnd file.");
            section.setTitleBarBackground(warningColor);
        }

        Composite composite = toolkit.createComposite(section, SWT.NONE);
        section.setClient(composite);

        composite.setLayout(new GridLayout(1, false));
    }

    private class FileOpenLinkListener extends HyperlinkAdapter {

        private final IPath path;

        public FileOpenLinkListener(IPath path) {
            this.path = path;
        }

        @Override
        public void linkActivated(HyperlinkEvent e) {
            IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
            FileEditorInput input = new FileEditorInput(file);

            try {
                IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                page.openEditor(input, "bndtools.bndWorkspaceConfigEditor", true);
            } catch (PartInitException ex) {
                ErrorDialog.openError(getSection().getShell(), "Error", "Unable to open editor", ex.getStatus());
            }
        }
    }

    @Override
    public void initialize(IManagedForm form) {
        super.initialize(form);

        try {
            Composite container = (Composite) getSection().getClient();

            IFile buildFile = Central.getWorkspaceBuildFile();
            if (buildFile == null)
                return;

            if (!mainFile) {
                ImageHyperlink link = form.getToolkit().createImageHyperlink(container, SWT.CENTER);
                link.setText("Open main build.bnd file.");
                link.setImage(bndFileImg);
                link.addHyperlinkListener(new FileOpenLinkListener(buildFile.getFullPath()));
            } else {
                IResource[] extFiles;

                IContainer cnfDir = buildFile.getParent();
                IFolder extDir = cnfDir.getFolder(new Path("ext"));
                if (extDir.exists())
                    extFiles = extDir.members();
                else
                    extFiles = new IResource[0];

                if (extFiles.length > 0) {
                    for (IResource extFile : extFiles) {
                        if (extFile.getType() == IResource.FILE && "bnd".equalsIgnoreCase(extFile.getFileExtension())) {
                            ImageHyperlink link = form.getToolkit().createImageHyperlink(container, SWT.CENTER);
                            link.setText("Open " + extFile.getName());
                            link.setImage(extFileImg);
                            link.addHyperlinkListener(new FileOpenLinkListener(extFile.getFullPath()));
                        }
                    }
                } else {
                    createMissingExtsWarningPanel(container, form.getToolkit(), extDir.getFullPath());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createMissingExtsWarningPanel(Composite parent, FormToolkit tk, IPath extPath) {
        Composite composite = tk.createComposite(parent);

        GridLayout layout = new GridLayout(2, false);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        composite.setLayout(layout);

        Label l1 = tk.createLabel(composite, "image");
        l1.setImage(warningImg);
        Label l2 = tk.createLabel(composite, String.format("No default configuration files found under %s", extPath));
        l2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    @Override
    public void dispose() {
        super.dispose();
        warningColor.dispose();
        bndFileImg.dispose();
        extFileImg.dispose();
        warningImg.dispose();
    }

}
