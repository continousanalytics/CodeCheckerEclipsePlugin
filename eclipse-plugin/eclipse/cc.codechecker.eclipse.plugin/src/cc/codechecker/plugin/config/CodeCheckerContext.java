package cc.codechecker.plugin.config;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import cc.codechecker.plugin.CodeCheckerNature;
import cc.codechecker.plugin.Logger;
import cc.codechecker.plugin.report.ReportParser;
import cc.codechecker.plugin.report.SearchList;
import cc.codechecker.plugin.views.report.list.ReportListView;
import cc.codechecker.plugin.views.report.list.ReportListViewCustom;
import cc.codechecker.plugin.views.report.list.ReportListViewListener;
import cc.codechecker.plugin.views.report.list.ReportListViewProject;
/**
 * The Class CodeCheckerContext.
 */
public class CodeCheckerContext {

    /** The instance. */
    static CodeCheckerContext instance;

    private static final String NULL_WINDOW = "Error: Active Workbench Window is null!";

    /** The active editor part. */
    IEditorPart activeEditorPart;

    /** The active project. */
    private IProject activeProject;

    private Map<IProject, SearchList> reports = new HashMap<>();
    
    private Map<IProject, CcConfiguration> configs = new HashMap<>();

    /**
     * Class constructor.
     */
    private CodeCheckerContext() {}

    /**
     * Returns a {@link CcConfiguration} object.
     * .
     * @param project The project in question.
     * @return If there is no CcConfiguration stored, then creates a new instance.
     */
    public CcConfiguration getConfigForProject(IProject project) {
    	if (!configs.containsKey(project)) 
    		setConfig(project, new CcConfiguration(project));
        CcConfiguration.logConfig(configs.get(project).getProjectConfig(null));
    	return configs.get(project);
    }

    /**
     * Store a {@link CcConfiguration} object associated with a project.
     * If there is a configuration already stored, it will be overwritten.
     * @param project The project in subject.
     * @param config The new configuration.
     */
    public void setConfig(IProject project, CcConfiguration config) {
        configs.put(project, config);
    }

    /**
	 *
	 * @return Returns all stored reports in a Map
	 */
    public Map<IProject, SearchList> getReports() {
        return reports;
    }

    /**
     * Store a {@link SearchList} associated with a project.
     * @param project The project in subject.
     * @param s The report container.
     */
    public void setReportForProject(IProject project, SearchList s){
        reports.put(project, s);
    }

    /**
     * The refresher for Project ReportList View.
     * 
     * @param pages the page list for the currently active workbench windows.
     * @param project the project, project the buglist to be refreshed
     * @param noFetch if true, the server will not be asked for new list 
     */
    private void refreshProject(IWorkbenchPage[] pages, IProject project, boolean noFetch) {
        Logger.log(IStatus.INFO, "Refreshing bug list for project:"+project.getName());
        for(IWorkbenchPage page : pages) {
            for (IViewReference vp : page.getViewReferences()) {
                if (vp.getId().equals(ReportListViewProject.ID)) {
                    ReportListViewProject rlvp = (ReportListViewProject) vp.getView(true);
                    if (!noFetch || this.activeProject != project) {
                        rlvp.onEditorChanged(project);
                    }
                }
            }
        }
    }

    /**
     * The refresher for Current ReportList View. 
     *
     * @param pages the page list for the currently active workbench windows
     * @param project the project, the user change his/her view to
     * @param filename the filename
     * @param considerViewerRefresh false if the refresh should always happen despite of no real need to force refresh
     */
    private void refreshCurrent(IWorkbenchPage[] pages, IProject project, String filename,
            boolean considerViewerRefresh) {
        for(IWorkbenchPage page : pages) {
            for (IViewReference vp : page.getViewReferences()) {
                if (vp.getId().equals(ReportListView.ID)) {
                    ReportListView rlv = (ReportListView) vp.getView(true);
                    if (!considerViewerRefresh || rlv.getViewerRefresh()) {
                        rlv.onEditorChanged(project, filename);
                    } else {
                        rlv.setViewerRefresh(true);
                    }
                }
            }
        }
    }

    /**
     * The refresher for Custom ReportList View. If secondary id is empty, 
     * it checks if a refresh really needs to happen and if so updates every 
     * custom view for the current project. If secondary-id is not empty, 
     * it will search for the secondary-id custom view and 
     * updates that particular one.
     *
     * @param pages the page list for the currently active workbench windows.
     * @param project the project, the user change his/her view to
     * @param secondaryId id of the {@link ReportListViewCustom} the refresh
     * @param considerProjectChange false if the refresh should always happen despite of no real need to force refresh
     */
    private void refreshCustom(IWorkbenchPage[] pages, IProject project, String secondaryId,
            boolean considerProjectChange) {
        for(IWorkbenchPage page : pages) {
            for (IViewReference vp : page.getViewReferences()) {
                if (vp.getId().equals(ReportListViewCustom.ID)) {
                    ReportListViewCustom rlvc = (ReportListViewCustom) vp.getView(true);
                    if(secondaryId.isEmpty() && rlvc.getViewSite().getSecondaryId() != null) {
                        if (!considerProjectChange || this.activeProject != project) {
                            rlvc.onEditorChanged(project);
                        }
                    } else if(rlvc.getViewSite().getSecondaryId() != null && 
                            rlvc.getViewSite().getSecondaryId().equals(secondaryId)){
                        rlvc.onEditorChanged(project);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Gets the single instance of CodeCheckerContext.
     *
     * @return CodeCheckerContext
     */
    public static CodeCheckerContext getInstance() {
        if (instance == null) {
            instance = new CodeCheckerContext();
        }
        return instance;
    }

    /**
     * Asynchronous refreshes the views.
     * @param project The project in context that the refresh was called.
     */
    public void refresAsync(IProject project){
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                refreshAfterBuild(project);
            }
        });
    }

    /**
     * Refresh after build.
     *
     * @param project the project, the user change his/her view to
     */
    private void refreshAfterBuild(final IProject project) {
        Logger.log(IStatus.INFO, "refreshAfterBuild");

        IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

        if(activeWindow == null) {
            Logger.log(IStatus.ERROR, NULL_WINDOW);
            return;
        }

        IWorkbenchPage[] pages = activeWindow.getPages();
        IWorkbenchPage activePage = activeWindow.getActivePage();
        if (activePage == null) {
            Logger.log(IStatus.INFO, " activePage is null!");
            return;
        }

        IEditorPart partRef = activePage.getActiveEditor();

        //partRef is null or partRef NOT instanceof FileEditor!
        if (partRef == null || !(partRef.getEditorInput() instanceof IFileEditorInput)) {
            this.refreshProject(pages, project, false);
            this.refreshCustom(pages, project, "", false);
            this.activeProject = project;
            Logger.log(IStatus.INFO, " partRef is null or partRef instanceof FileEditor!");
            return;
        }


        activeEditorPart = partRef;
        IFile file = ((IFileEditorInput) partRef.getEditorInput()).getFile();

        if (project!=this.activeProject){
            //Nullptr on activeprocejt on pluginstart
            //Logger.log(IStatus.INFO, "New results do not refer to the active project"+this.activeProject.getName());
            return;
        }

        CcConfiguration config = getConfigForProject(project);

        //The actual refresh happens here.
        String filename = config.getAsProjectRelativePath(file.getProjectRelativePath().toString());
        this.refreshCurrent(pages, project, filename, false);
        this.refreshProject(pages, project, false);
        this.refreshCustom(pages, project, "", false);
        this.activeProject = project;
    }

    /**
     * Refresh change editor part.
     *
     * @param partRef the IEditorPart which the user has switched.
     */
    public void refreshChangeEditorPart(IEditorPart partRef) {        
        if (partRef.getEditorInput() instanceof IFileEditorInput){
            //could be FileStoreEditorInput
            //for files which are not part of the
            //current workspace
            activeEditorPart = partRef;
            IFile file = ((IFileEditorInput) partRef.getEditorInput()).getFile();
            IProject project = file.getProject();
            try {
                if (project.hasNature(CodeCheckerNature.NATURE_ID)) {
                    CcConfiguration config = getConfigForProject(project);

                    String filename = config.getAsProjectRelativePath(file.getProjectRelativePath().toString());

                    IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                    if (activeWindow == null) {
                        Logger.log(IStatus.ERROR, NULL_WINDOW);
                        return;
                    }
                    IWorkbenchPage[] pages = activeWindow.getPages();

                    this.refreshProject(pages, project, true);
                    this.refreshCurrent(pages, project, filename, true);
                    this.refreshCustom(pages, project, "", true);
                    this.activeProject = project;
                }
            } catch (CoreException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * Refresh change project.
     *
     * @param project the project, the user change his/her view to
     */
    public void refreshChangeProject(IProject project) {
        IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

        if(activeWindow == null) {
            Logger.log(IStatus.ERROR, NULL_WINDOW);
            return;
        }

        IWorkbenchPage[] pages = activeWindow.getPages();

        this.refreshProject(pages, project, true);
        this.refreshCustom(pages, project, "", true);
        this.activeProject = project;
    }

    /**
     * Refresh add custom report list view.
     *
     * @param secondaryId the ReportListCustomView secondary id
     */
    public void refreshAddCustomReportListView(String secondaryId) {
        IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

        if(activeWindow == null) {
            Logger.log(IStatus.ERROR, NULL_WINDOW);
            return;
        }

        IEditorPart partRef = activeWindow.getActivePage().getActiveEditor();
        if(partRef == null) {
            Logger.log(IStatus.INFO, "partRef is null!");
            return;
        }
        activeEditorPart = partRef;
        IFile file = ((IFileEditorInput) partRef.getEditorInput()).getFile();
        IProject project = file.getProject();

        IWorkbenchPage[] pages = activeWindow.getPages();

        this.refreshCustom(pages, project, secondaryId, true);
        this.activeProject = project;
    }

    /**
     * Run report job.
     * TODO refactor this to a report job.
     * @param target the target
     * @param currentFileName the run id
     */
    public void runReportJob(ReportListView target, String currentFileName) {
        IProject project = target.getCurrentProject();
        if (project == null) return;
        Logger.log(IStatus.INFO, "Started Filtering Reports for project: "+project.getName());

        ReportParser parser = new ReportParser(reports.get(project), currentFileName);
        // add listeners to it.
        parser.addListener(new ReportListViewListener(target));
        Display.getDefault().asyncExec(parser);
        Logger.log(IStatus.INFO, "Finished Filtering Reports for project: "+project.getName());
    }
}
