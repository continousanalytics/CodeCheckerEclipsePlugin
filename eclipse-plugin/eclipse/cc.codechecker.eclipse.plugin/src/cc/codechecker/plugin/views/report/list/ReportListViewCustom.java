package cc.codechecker.plugin.views.report.list;

import java.util.LinkedList;

import org.eclipse.core.resources.IProject;

import cc.codechecker.plugin.config.filter.Filter;
import cc.codechecker.plugin.config.filter.FilterConfiguration;

public class ReportListViewCustom extends ReportListView {

    public static final String ID = "cc.codechecker.plugin.views.ReportListViewCustom";

    public void onEditorChanged(IProject project) {
        super.onEditorChanged(project, "");
    }

}
