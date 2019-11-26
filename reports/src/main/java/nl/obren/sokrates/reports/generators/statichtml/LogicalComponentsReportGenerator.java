package nl.obren.sokrates.reports.generators.statichtml;

import nl.obren.sokrates.common.renderingutils.RichTextRenderingUtils;
import nl.obren.sokrates.common.utils.FormattingUtils;
import nl.obren.sokrates.reports.charts.SimpleOneBarChart;
import nl.obren.sokrates.reports.core.RichTextReport;
import nl.obren.sokrates.reports.utils.GraphvizDependencyRenderer;
import nl.obren.sokrates.reports.utils.ScopesRenderer;
import nl.obren.sokrates.sourcecode.analysis.results.CodeAnalysisResults;
import nl.obren.sokrates.sourcecode.analysis.results.LogicalDecompositionAnalysisResults;
import nl.obren.sokrates.sourcecode.aspects.SourceCodeAspect;
import nl.obren.sokrates.sourcecode.dependencies.ComponentDependency;
import nl.obren.sokrates.sourcecode.dependencies.DependencyUtils;
import nl.obren.sokrates.sourcecode.metrics.NumericMetric;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class LogicalComponentsReportGenerator {
    private CodeAnalysisResults codeAnalysisResults;
    private boolean elaborate = true;

    public LogicalComponentsReportGenerator(CodeAnalysisResults codeAnalysisResults) {
        this.codeAnalysisResults = codeAnalysisResults;
    }

    public void addCodeOrganizationToReport(RichTextReport report) {
        addSummary(report);
        addErrors(report);
        addFooter(report);
    }

    private void addErrors(RichTextReport report) {
        codeAnalysisResults.getLogicalDecompositionsAnalysisResults().forEach(result -> {
            if (result.getComponentDependenciesErrors().size() > 0) {
                report.startSection("WARNINGS (" + result.getComponentDependenciesErrors().size() + ")", "Places where dependencies cannot be resolved uniquely");
                report.startUnorderedList();
                result.getComponentDependenciesErrors().forEach(error ->
                        report.addListItem(result.getKey() + ": " + error.getMessage() + " " + error.getFiltering())
                );
                report.endUnorderedList();
                report.endSection();
            }
        });
    }

    private void addFooter(RichTextReport report) {
        report.addLineBreak();
        report.addHorizontalLine();
        report.addParagraph(RichTextRenderingUtils.italic(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())));
    }

    private void addSummary(RichTextReport report) {
        report.startSection("Intro", "");
        if (elaborate) {
            appendIntroduction(report);
        }

        int size = codeAnalysisResults.getLogicalDecompositionsAnalysisResults().size();
        report.addParagraph("Analyzed system has <b>" + size + "</b> logical decomposition" + (size > 1 ? "s" : "") + ":");
        report.startUnorderedList();
        codeAnalysisResults.getLogicalDecompositionsAnalysisResults().forEach(logicalDecomposition -> {
            int componentsCount = logicalDecomposition.getComponents().size();
            report.addListItem(logicalDecomposition.getLogicalDecomposition().getName() + " (" + componentsCount + " component" + (componentsCount > 1 ? "s" : "") + ")");
        });
        report.endUnorderedList();
        report.endSection();

        int[] sectionIndex = {1};
        codeAnalysisResults.getLogicalDecompositionsAnalysisResults().forEach(logicalDecomposition -> {
            report.startSection("Logical Decomposition #" + sectionIndex[0] + ": " + logicalDecomposition.getKey().toUpperCase(), getDecompositionDescription(logicalDecomposition));

            List<NumericMetric> fileCountPerExtension = logicalDecomposition.getFileCountPerComponent();
            List<NumericMetric> linesOfCodePerExtension = logicalDecomposition.getLinesOfCodePerComponent();

            ScopesRenderer renderer = new ScopesRenderer();
            renderer.setLinesOfCodeInMain(codeAnalysisResults.getMainAspectAnalysisResults().getLinesOfCode());

            renderer.setTitle("Components");
            renderer.setDescription("");
            renderer.setFileCountPerComponent(fileCountPerExtension);
            renderer.setLinesOfCode(linesOfCodePerExtension);
            renderer.setMaxFileCount(codeAnalysisResults.getMaxFileCount());
            renderer.setMaxLinesOfCode(codeAnalysisResults.getMaxLinesOfCode());
            renderer.renderReport(report, "The \"" + logicalDecomposition.getLogicalDecomposition().getName() + "\" logical decomposition has <b>" + logicalDecomposition.getLogicalDecomposition().getComponents().size() + "</b> components.");

            report.startSubSection("Alternative Visuals", "");
            report.startUnorderedList();
            report.addListItem("<a href='visuals/bubble_chart_components_" + (sectionIndex[0]) + ".html'>Bubble Chart</a>");
            report.addListItem("<a href='visuals/tree_map_components_" + (sectionIndex[0]) + ".html'>Tree Map</a>");
            report.endUnorderedList();
            report.endSection();

            List<ComponentDependency> componentDependencies = logicalDecomposition.getComponentDependencies();
            report.startSubSection("Dependencies", "Dependencies among components are <b>static</b> code dependencies among files in different components.");
            if (componentDependencies != null && componentDependencies.size() > 0) {
                addComponentDependeciesSection(report, logicalDecomposition, componentDependencies);
            } else {
                report.addParagraph("No component dependencies found.");
            }
            report.endSection();
            report.endSection();
            sectionIndex[0]++;
        });
    }

    private void addComponentDependeciesSection(RichTextReport report, LogicalDecompositionAnalysisResults logicalDecomposition, List<ComponentDependency> componentDependencies) {
        report.startUnorderedList();
        report.addListItem("Analyzed system has <b>" + componentDependencies.size() + "</b> links (arrows) between components.");
        report.addListItem("The number on the arrow represents the number of files from referring component that depend on files in referred component.");
        report.addListItem("These " + componentDependencies.size() + " links contain <b>" + DependencyUtils.getDependenciesCount(componentDependencies) + "</b> dependencies.");
        int cyclicDependencyPlacesCount = DependencyUtils.getCyclicDependencyPlacesCount(componentDependencies);
        int cyclicDependencyCount = DependencyUtils.getCyclicDependencyCount(componentDependencies);
        if (cyclicDependencyPlacesCount > 0) {
            String numberOfPlacesText = cyclicDependencyPlacesCount == 1
                    ? "is <b>1</b> place"
                    : "are <b>" + cyclicDependencyPlacesCount + "</b> places";
            report.addListItem("There " + numberOfPlacesText + " (" + (cyclicDependencyPlacesCount * 2) + " links) with <b>cyclic</b> dependencies (<b>" + cyclicDependencyCount + "</b> " +
                    "file dependencies).");
        }
        report.endUnorderedList();
        List<String> componentNames = new ArrayList<>();
        logicalDecomposition.getComponents().forEach(c -> componentNames.add(c.getName()));
        GraphvizDependencyRenderer graphvizDependencyRenderer = new GraphvizDependencyRenderer();
        graphvizDependencyRenderer.setOrientation(logicalDecomposition.getLogicalDecomposition().getRenderingOrientation());

        String graphvizContent = graphvizDependencyRenderer.getGraphvizContent(componentNames, componentDependencies);
        report.addGraphvizFigure("", graphvizContent);
        report.addLineBreak();
        report.addShowMoreBlock("",
                "<textarea style='width:90%; height: 20em; font-family: Courier New; color: grey'>"
                        + graphvizContent
                        + "</textarea>", "graphviz code...");

        report.addLineBreak();
        report.addLineBreak();
        report.startTable();
        report.addTableHeader("From", "From Files", "->", "To");
        Collections.sort(componentDependencies, (o1, o2) -> o2.getCount() - o1.getCount());
        componentDependencies.forEach(componentDependency -> {
            report.startTableRow();
            report.addTableCell(componentDependency.getFromComponent());
            report.addHtmlContent("<td>");
            int locFromDuplications = componentDependency.getLocFrom();
            SourceCodeAspect fromComponentByName = logicalDecomposition.getLogicalDecomposition().getComponentByName(componentDependency.getFromComponent());
            String percentageHtmlFragment = null;
            if (fromComponentByName != null) {
                SimpleOneBarChart chart = new SimpleOneBarChart();
                chart.setWidth(240);
                chart.setMaxBarWidth(120);
                chart.setBarHeight(10);
                chart.setBarStartXOffset(2);
                double percentage = 100.0 * locFromDuplications / fromComponentByName.getLinesOfCode();
                String percentageText = FormattingUtils.getFormattedPercentage(percentage) + "%";
                percentageHtmlFragment = chart.getPercentageSvg(percentage, "", locFromDuplications + " LOC (" + percentageText + ")");
            }

            report.addShowMoreBlock("",
                    "<textarea style='width:90%; height: 20em; font-family: Courier New; color: grey'>"
                            + componentDependency.getPathsFrom().stream().collect(Collectors.joining("\n")) +
                            "</textarea>",
                    componentDependency.getCount() + " files"
                            + (percentageHtmlFragment != null ? "<br/>" + percentageHtmlFragment : " (" + locFromDuplications + " LOC)")
            );
            report.addHtmlContent("</td>");
            report.addTableCell("&nbsp;->&nbsp;");
            report.addTableCell(componentDependency.getToComponent());
            report.endTableRow();
        });
        report.endTable();
    }

    private String getDecompositionDescription(LogicalDecompositionAnalysisResults logicalDecomposition) {
        int numberOfComponents = logicalDecomposition.getComponents().size();
        String decompositionDescription = "";
        if (logicalDecomposition.getLogicalDecomposition().getComponentsFolderDepth() > 0) {
            decompositionDescription = "The decompositions is based on folder structure at <b>level " + logicalDecomposition.getLogicalDecomposition().getComponentsFolderDepth() + "</b> (relative to the source code root).";
        } else if (logicalDecomposition.getComponents() != null && numberOfComponents > 0) {
            decompositionDescription = "The \"" + logicalDecomposition.getLogicalDecomposition().getName() + "\" logical decomposition in based on <b>" + numberOfComponents + "</b> explicitly defined " +
                    "components.";
        }
        return decompositionDescription;
    }

    private void appendIntroduction(RichTextReport report) {
        String shortIntro = "";
        shortIntro += "<b>Logical decomposition</b> is a representation of the organization of the <b>main</b> source code, where every and each file is " +
                "put in exactly one <b>logical component</b>.";
        shortIntro += "";

        String longIntro = "<ul>\n";
        longIntro += "<li>A software system can have <b>one</b> or <b>more</b> logical decompositions.</li>\n";
        longIntro += "<li>A logical decomposition can be defined in two ways in Sokrates.</li>\n";
        longIntro += "<li>First approach is based on the <b>folders structure</b>. " +
                "Components are mapped to folders at defined <b>folder depth</b> relative to the source code root.</li>\n";
        longIntro += "<li>Second approach is based on <b>explicit</b> definition of each component. In such explicit definitions, components are " +
                "explicitly <b>named</b> and their files are selected based on explicitly defined path and content <b>filters</b>.</li>\n";
        longIntro += "<li>A logical decomposition is considered <b>invalid</b> if a file is selected into <b>two or more components</b>." +
                "This constraint is introduced in order to facilitate measuring of <b>dependencies</b> among components.</li>\n";
        longIntro += "<li>Files not assigned to any component are put into a special \"<b>Unclassified</b>\" component.</li>\n";

        longIntro += "</ul>\n";


        report.addParagraph(shortIntro);
        report.addHtmlContent(longIntro);

        report.startUnorderedList();
        report.addListItem("To learn more about good practices on componentization and dependencies, Sokrates recommends the following resources:");
        report.startUnorderedList();
        report.addListItem("<a target='_blank' href='https://www.martinfowler.com/ieeeSoftware/coupling.pdf'>Reduce Coupling</a>, MartinFlower.com (IEEE Software article)");
        report.addListItem("<a target='_blank' href='https://sourcemaking.com/refactoring/smells/couplers'>Couplers Code Smells</a>, sourcemaking.com");
        report.endUnorderedList();
        report.endUnorderedList();
    }


}
