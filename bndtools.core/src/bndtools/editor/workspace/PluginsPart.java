package bndtools.editor.workspace;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.SectionPart;
import org.eclipse.ui.forms.editor.IFormPage;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import aQute.lib.osgi.Constants;
import bndtools.Plugin;
import bndtools.editor.model.BndEditModel;
import bndtools.model.clauses.HeaderClause;

public class PluginsPart extends SectionPart implements PropertyChangeListener {

    private List<HeaderClause> data;

    private Table table;
    private TableViewer viewer;

    private Image editImg;
    private ToolItem editItemTool;
    private ToolItem removeItemTool;

    private BndEditModel model;

    public PluginsPart(Composite parent, FormToolkit toolkit, int style) {
        super(parent, toolkit, style);
        createSection(getSection(), toolkit);
    }

    final void createSection(Section section, FormToolkit toolkit) {
        section.setText("Plugins");
        section.setDescription("Bnd plugins are used to specify repositories and extended behaviours.");

        createToolBar(section);

        table = toolkit.createTable(section, SWT.FULL_SELECTION | SWT.MULTI | SWT.BORDER);

        viewer = new TableViewer(table);
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setLabelProvider(new PluginClauseLabelProvider());

        // Listeners
        viewer.addSelectionChangedListener(new ISelectionChangedListener() {
            public void selectionChanged(SelectionChangedEvent event) {
                boolean enable = !viewer.getSelection().isEmpty();
                removeItemTool.setEnabled(enable);
                editItemTool.setEnabled(enable);
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if(e.character == SWT.DEL) {
                    doRemove();
                } else if(e.character == '+') {;
                    doAdd();
                }
            }
        });

        section.setClient(table);
    }

    void createToolBar(Section section) {
        ToolBar toolbar = new ToolBar(section, SWT.FLAT);
        section.setTextClient(toolbar);

        ToolItem addItem = new ToolItem(toolbar, SWT.PUSH);
        addItem.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ADD));
        addItem.setToolTipText("Add Plugin");
        addItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                doAdd();
            }
        });

        editItemTool = new ToolItem(toolbar, SWT.PUSH);
        editImg = AbstractUIPlugin.imageDescriptorFromPlugin(Plugin.PLUGIN_ID, "/icons/pencil.png").createImage();
        editItemTool.setImage(editImg);
        editItemTool.setToolTipText("Edit");
        editItemTool.setEnabled(false);
        editItemTool.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                doEdit();
            }
        });

        removeItemTool = new ToolItem(toolbar, SWT.PUSH);
        removeItemTool.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_DELETE));
        removeItemTool.setDisabledImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_DELETE_DISABLED));
        removeItemTool.setToolTipText("Remove");
        removeItemTool.setEnabled(false);
        removeItemTool.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                doRemove();
            }
        });
    }

    @Override
    public void initialize(IManagedForm form) {
        super.initialize(form);

        model = (BndEditModel) form.getInput();
        model.addPropertyChangeListener(Constants.PLUGIN, this);
    }

    @Override
    public void dispose() {
        super.dispose();
        if(model != null) model.removePropertyChangeListener(Constants.PLUGIN, this);
        editImg.dispose();
    }

    @Override
    public void refresh() {
        List<HeaderClause> modelData = model.getPlugins();
        if (modelData != null)
            this.data = new ArrayList<HeaderClause>(modelData);
        else
            this.data = new LinkedList<HeaderClause>();
        viewer.setInput(this.data);
        super.refresh();
    }

    @Override
    public void commit(boolean onSave) {
        super.commit(onSave);
        model.setPlugins(data);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        IFormPage page = (IFormPage) getManagedForm().getContainer();
        if(page.isActive()) {
            refresh();
        } else {
            markStale();
        }
    }

    void doAdd() {
        PluginSelectionWizard wizard = new PluginSelectionWizard();
        WizardDialog dialog = new WizardDialog(getManagedForm().getForm().getShell(), wizard);
        if (dialog.open() == Window.OK) {
            HeaderClause newPlugin = wizard.getHeader();

            data.add(newPlugin);
            viewer.add(newPlugin);
            markDirty();
        }
    }

    void doEdit() {

    }

    void doRemove() {
        IStructuredSelection sel = (IStructuredSelection) viewer.getSelection();

        viewer.remove(sel.toArray());
        data.removeAll(sel.toList());

        if (!sel.isEmpty())
            markDirty();
    }

    public ISelectionProvider getSelectionProvider() {
        return viewer;
    }


}