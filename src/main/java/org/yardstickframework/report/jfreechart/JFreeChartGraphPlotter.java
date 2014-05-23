/*
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.yardstickframework.report.jfreechart;

import com.beust.jcommander.*;
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.entity.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.title.*;
import org.jfree.data.xy.*;
import org.jfree.ui.*;
import org.yardstickframework.writers.*;

import java.awt.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.List;

import static java.awt.Color.*;
import static org.yardstickframework.BenchmarkUtils.*;
import static org.yardstickframework.report.jfreechart.JFreeChartGenerationMode.*;
import static org.yardstickframework.writers.BenchmarkProbePointCsvWriter.*;

/**
 * JFreeChart graph plotter.
 */
public class JFreeChartGraphPlotter {
    /** */
    private static final String INPUT_FILE_EXTENSION = ".csv";

    /** */
    private static final Color[] PLOT_COLORS = {GREEN, BLUE, RED, ORANGE, CYAN, MAGENTA,
        new Color(255, 0, 137), new Color(163, 143, 255), new Color(76, 255, 153)};

    /**
     * @param cmdArgs Arguments.
     */
    public static void main(String[] cmdArgs) {
        try {
            JFreeChartGraphPlotterArguments args = new JFreeChartGraphPlotterArguments();

            JCommander jCommander = jcommander(cmdArgs, args, "<graph-plotter>");

            if (args.help()) {
                jCommander.usage();

                return;
            }

            if (args.inputFolders().isEmpty()) {
                errorHelp("Input folders are not defined.");

                return;
            }

            List<String> inFoldersAsString = args.inputFolders();

            List<File> inFolders = new ArrayList<>(inFoldersAsString.size());

            for (String folderAsString : inFoldersAsString)
                inFolders.add(new File(folderAsString).getAbsoluteFile());

            for (File inFolder : inFolders) {
                if (!inFolder.exists()) {
                    errorHelp("Folder does not exist: " + inFolder.getAbsolutePath());

                    return;
                }
            }

            JFreeChartGenerationMode mode = args.generationMode();

            if (mode == COMPOUND)
                processCompoundMode(inFolders, args);
            else if (mode == COMPARISON)
                processComparisonMode(inFolders, args);
            else if (mode == STANDARD)
                processStandardMode(inFolders, args);
            else
                errorHelp("Unknown generation mode: " + args.generationMode());
        }
        catch (ParameterException e) {
            errorHelp("Invalid parameter.", e);
        }
        catch (Exception e) {
            errorHelp("Failed to execute graph generator.", e);
        }
    }

    /**
     * @param inFolders Input folders.
     * @param args Arguments.
     * @throws Exception If failed.
     */
    private static void processCompoundMode(List<File> inFolders, JFreeChartGraphPlotterArguments args) throws Exception {
        Map<String, List<File>> res = new HashMap<>();

        for (File inFolder : inFolders) {
            Map<String, List<File>> map = files(inFolder);

            mergeMaps(res, map);
        }

        Set<String> folders = new HashSet<>();

        for (List<File> files : res.values()) {
            for (File file : files) {
                File par = file.getParentFile();

                if (par != null)
                    folders.add(par.getName());
            }
        }

        if (res.isEmpty())
            return;

        StringBuilder outFolSuf = new StringBuilder();

        for (String f : folders) {
            String s = parseTime(f);

            if (!s.isEmpty())
                outFolSuf.append(s).append('_');
        }

        if (outFolSuf.length() > 0)
            outFolSuf.delete(outFolSuf.length() - 1, outFolSuf.length());

        String parent = outputFolder(inFolders);

        String parentFolderName = "results_" + COMPOUND.name().toLowerCase() + '_' + outFolSuf.toString();

        parentFolderName = fixFolderName(parentFolderName);

        File folderToWrite = new File(parent, parentFolderName);

        if (!folderToWrite.exists()) {
            if (!folderToWrite.mkdir())
                throwException("Can not create folder: " + folderToWrite.getAbsolutePath());
        }

        processFilesPerProbe(res, folderToWrite, args, COMPOUND);
    }

    /**
     * @param inFolders Input folders.
     * @param args Arguments.
     * @throws Exception If failed.
     */
    private static void processComparisonMode(List<File> inFolders, JFreeChartGraphPlotterArguments args) throws Exception {
        Collection<File[]> foldersToCompare = new ArrayList<>();

        StringBuilder outParentFolSuf = new StringBuilder();

        for (File inFolder : inFolders) {
            File[] dirs = inFolder.listFiles();

            if (dirs == null || dirs.length == 0)
                continue;

            foldersToCompare.add(dirs);

            String fName = inFolder.getName();

            String s = fName.startsWith("results_") ? fName.replace("results_", "") : "";

            if (!s.isEmpty())
                outParentFolSuf.append(s).append('_');
        }

        if (outParentFolSuf.length() > 0)
            outParentFolSuf.delete(outParentFolSuf.length() - 1, outParentFolSuf.length());

        String parent = outputFolder(inFolders);

        String parentFolderName = "results_" + COMPARISON.name().toLowerCase() + '_' + outParentFolSuf.toString();

        parentFolderName = fixFolderName(parentFolderName);

        File parentFolderToWrite = new File(parent, parentFolderName);

        int idx = -1;

        while (true) {
            idx++;

            boolean filesExist = false;

            Map<String, List<File>> res = new HashMap<>();

            for (File[] files : foldersToCompare) {
                if (files.length <= idx)
                    continue;

                filesExist = true;

                File f = files[idx];

                if (f.isDirectory()) {
                    Map<String, List<File>> map = files(f);

                    mergeMaps(res, map);
                }
            }

            if (!filesExist)
                break;

            String idxPrefix = idx < 9 ? "00" : idx < 99 ? "0" : "";

            String folName = idxPrefix + (idx + 1);

            folName = fixFolderName(folName);

            File folderToWrite = new File(parentFolderToWrite, folName);

            if (!folderToWrite.exists()) {
                if (!folderToWrite.mkdirs())
                    throwException("Can not create folder: " + folderToWrite.getAbsolutePath());
            }

            processFilesPerProbe(res, folderToWrite, args, COMPARISON);
        }
    }

    /**
     * @param inFolders Input folders.
     * @param args Arguments.
     * @throws Exception If failed.
     */
    private static void processStandardMode(List<File> inFolders, JFreeChartGraphPlotterArguments args) throws Exception {
        for (File inFolder : inFolders) {
            Map<String, List<JFreeChartPlotInfo>> infoMap = new HashMap<>();

            for (List<File> files : files(inFolder).values()) {
                for (File file : files) {
                    println("Processing file: " + file.getAbsolutePath());

                    try {
                        List<PlotData> plotData = readData(file);

                        processPlots(file.getParentFile(), Collections.singleton(plotData), infoMap, STANDARD);
                    }
                    catch (Exception e) {
                        errorHelp("Exception is raised while processing file (will skip): " + file.getAbsolutePath(), e);
                    }
                }
            }

            JFreeChartResultPageGenerator.generate(inFolder, args, infoMap);
        }
    }

    /**
     * @param res Resulted map.
     * @param folderToWrite Folder to write results to.
     * @param args Arguments.
     * @param mode Generation mode.
     * @throws Exception If failed.
     */
    private static void processFilesPerProbe(Map<String, List<File>> res, File folderToWrite,
        JFreeChartGraphPlotterArguments args, JFreeChartGenerationMode mode) throws Exception {
        Map<String, List<JFreeChartPlotInfo>> infoMap = new HashMap<>();

        for (Map.Entry<String, List<File>> entry : res.entrySet()) {
            Collection<List<PlotData>> plots = new ArrayList<>(entry.getValue().size());

            for (File file : entry.getValue()) {
                println("Processing file: " + file.getAbsolutePath());

                try {
                    plots.add(readData(file));
                }
                catch (Exception e) {
                    errorHelp("Exception is raised while processing file (will skip): " + file.getAbsolutePath(), e);
                }
            }

            processPlots(folderToWrite, plots, infoMap, mode);
        }

        if (!infoMap.isEmpty())
            JFreeChartResultPageGenerator.generate(folderToWrite, args, infoMap);
    }

    /**
     * @param fName Folder name.
     * @return Substring containing benchmark time.
     */
    private static String parseTime(String fName) {
        int i = fName.indexOf('_', fName.indexOf('_') + 1);

        if (i != -1) {
            try {
                String time = fName.substring(0, i);

                BenchmarkProbePointCsvWriter.FORMAT.parse(time);

                return time;
            }
            catch (ParseException ignored) {
                return "";
            }
        }

        return "";
    }

    /**
     * @param res Resulted map.
     * @param map Map to merge.
     */
    private static void mergeMaps(Map<String, List<File>> res, Map<String, List<File>> map) {
        for (Map.Entry<String, List<File>> entry : map.entrySet()) {
            List<File> list = res.get(entry.getKey());

            if (list == null) {
                list = new ArrayList<>();

                res.put(entry.getKey(), list);
            }

            list.addAll(entry.getValue());
        }
    }

    /**
     * @param folder Folder to scan for files.
     * @return Collection of files.
     */
    private static Map<String, List<File>> files(File folder) {
        File[] dirs = folder.listFiles();

        if (dirs == null || dirs.length == 0)
            return Collections.emptyMap();

        Map<String, List<File>> res = new HashMap<>();

        for (File dir : dirs) {
            if (dir.isDirectory()) {
                File[] files = dir.listFiles();

                if (files == null || files.length == 0)
                    continue;

                for (File file : files)
                    addFile(file, res);
            }
            else
                addFile(dir, res);
        }

        return res;
    }

    /**
     * @param file File to add.
     * @param res Resulted collection.
     */
    private static void addFile(File file, Map<String, List<File>> res) {
        if (file.isDirectory())
            return;

        if (!file.canRead()) {
            errorHelp("File can not be read: " + file.getAbsolutePath());

            return;
        }

        if (file.getName().endsWith(INPUT_FILE_EXTENSION)) {
            List<File> list = res.get(file.getName());

            if (list == null) {
                list = new ArrayList<>();

                res.put(file.getName(), list);
            }

            list.add(file);
        }
    }

    /**
     * @param folderToWrite Folder to write the resulted charts.
     * @param plots Collections of plots.
     * @param infoMap Map with additional plot info.
     * @param mode Generation mode.
     * @throws Exception If failed.
     */
    private static void processPlots(File folderToWrite, Collection<List<PlotData>> plots,
        Map<String, List<JFreeChartPlotInfo>> infoMap, JFreeChartGenerationMode mode) throws Exception {
        ChartRenderingInfo info = new ChartRenderingInfo(new StandardEntityCollection());

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);

        int idx = -1;

        while (true) {
            idx++;

            DefaultXYDataset dataSet = new DefaultXYDataset();

            List<JFreeChartPlotInfo> infoList = new ArrayList<>();

            String xAxisLabel = "";
            String yAxisLabel = "";
            String plotName = "";

            for (List<PlotData> plotData0 : plots) {
                if (plotData0.size() <= idx)
                    continue;

                PlotData plotData = plotData0.get(idx);

                dataSet.addSeries(plotData.series().seriesName, plotData.series().data);

                xAxisLabel = plotData.xAxisLabel;
                yAxisLabel = plotData.yAxisLabel;
                plotName = plotData.plotName();

                infoList.add(info(plotData.series(), mode));
            }

            if (infoList.isEmpty())
                break;

            JFreeChart chart = ChartFactory.createXYLineChart(
                "",
                xAxisLabel,
                yAxisLabel,
                dataSet,
                PlotOrientation.VERTICAL,
                false,
                false,
                false);

            AxisSpace as = new AxisSpace();

            as.add(150, RectangleEdge.LEFT);

            XYPlot plot = (XYPlot)chart.getPlot();

            BasicStroke stroke = new BasicStroke(1);

            plot.setRenderer(renderer);
            plot.setBackgroundPaint(WHITE);
            plot.setRangeGridlinePaint(GRAY);
            plot.setDomainGridlinePaint(GRAY);
            plot.setFixedRangeAxisSpace(as);
            plot.setOutlineStroke(stroke);

            for (int i = 0; i < infoList.size(); i++) {
                Color color = PLOT_COLORS[i % PLOT_COLORS.length];

                renderer.setSeriesPaint(i, color);
                renderer.setSeriesStroke(i, new BasicStroke(3)); // Line thickness.

                infoList.get(i).color(Integer.toHexString(color.getRGB()).substring(2));
            }

            ValueAxis axis = plot.getRangeAxis();

            Font font = new Font(axis.getTickLabelFont().getName(), Font.BOLD, axis.getTickLabelFont().getSize() + 3);

            axis.setTickLabelFont(font);
            axis.setLabelFont(font);
            plot.getDomainAxis().setTickLabelFont(font);
            plot.getDomainAxis().setLabelFont(font);

            chart.setTitle(new TextTitle(yAxisLabel, new Font(font.getName(), font.getStyle(), 30)));

            File res = new File(folderToWrite, plotName + ".png");

            ChartUtilities.saveChartAsPNG(res, chart, 800, 400, info);

            infoMap.put(res.getAbsolutePath(), infoList);

            println("Resulted chart is saved to file: " + res.getAbsolutePath());
        }

        System.out.println();
    }

    /**
     * @param series Plot series.
     * @param mode Generation mode.
     * @return Graph info.
     */
    private static JFreeChartPlotInfo info(PlotSeries series, JFreeChartGenerationMode mode) {
        double sum = 0;
        double min = Long.MAX_VALUE;
        double max = Long.MIN_VALUE;

        int len = series.data[1].length;

        if (len == 1) {
            double val = series.data[1][0];

            return new JFreeChartPlotInfo(series.seriesName, val, val, val, 0, mode);
        }

        for (int i = 0; i < len; i++) {
            double val = series.data[1][i];

            min = Math.min(min, val);

            max = Math.max(max, val);

            sum += val;
        }

        double avg = sum / len;

        double s = 0;

        for (int i = 0; i < len; i++) {
            double val = series.data[1][i];

            s += Math.pow((val - avg), 2);
        }

        double stdDiv = Math.sqrt(s / (len - 1));

        return new JFreeChartPlotInfo(series.seriesName, avg, min, max, stdDiv, mode);
    }

    /**
     * @param file File.
     * @return Collection of plot data.
     * @throws Exception If failed.
     */
    private static List<PlotData> readData(File file) throws Exception {
        List<PlotData> data = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            long initTime = 0;

            String[] metaInfo = null;

            for (String line; (line = br.readLine()) != null;) {
                if (line.startsWith("--"))
                    continue;

                if (line.startsWith(META_INFO_PREFIX)) {
                    metaInfo = line.substring(META_INFO_PREFIX.length()).split("\"" + META_INFO_SEPARATOR + "\"");

                    continue;
                }

                String[] split = line.split(",");

                if (data.isEmpty()) {
                    initTime = Long.parseLong(split[0]);

                    int plotNum = split.length - 1;

                    if (plotNum < 1)
                        throwException("Invalid data file: " + file.getAbsolutePath());

                    String xAxisLabel = metaInfo == null || metaInfo.length == 0 ? "" : metaInfo[0].replace("\"", "");

                    for (int i = 0; i < plotNum; i++) {
                        PlotSeries single = new PlotSeries(file.getParentFile().getName());

                        String yAxisLabel = metaInfo == null || i + 1 >= metaInfo.length ? "" :
                            metaInfo[i + 1].replace("\"", "");

                        String plotName = file.getName().replace(INPUT_FILE_EXTENSION, "");

                        String cnt = Integer.toString(i + 1);

                        cnt = cnt.length() == 1 ? "0" + cnt : cnt;

                        data.add(new PlotData("Plot_" + plotName + "_" + cnt, single, xAxisLabel, yAxisLabel));
                    }
                }

                double[] tup = new double[split.length];

                for (int i = 0; i < tup.length; i++) {
                    double d = i == 0 ? (Long.parseLong(split[0]) - initTime) : Double.parseDouble(split[i]);

                    tup[i] = d;
                }

                for (int i = 0; i < split.length - 1; i++)
                    data.get(i).series().rawData.add(new double[] {tup[0], tup[i + 1]});
            }

            for (PlotData plotData : data)
                plotData.series().finish();

            return data;
        }
    }

    /**
     * @param inFolders Input folders.
     * @return Output folder name.
     */
    private static String outputFolder(List<File> inFolders) {
        return inFolders.size() != 1 ? null :
            inFolders.get(0).getParent() == null ? inFolders.get(0).getName() : inFolders.get(0).getParent();
    }

    /**
     * Prints message.
     *
     * @param msg Message.
     */
    static void println(String msg) {
        System.out.println(msg);
    }

    /**
     * Throws exception.
     *
     * @param msg Error message.
     * @throws Exception that describes exceptional situation.
     */
    private static void throwException(String msg) throws Exception {
        throw new Exception("ERROR: " + msg);
    }

    /**
     * Prints error and help.
     *
     * @param msg Error message.
     */
    static void errorHelp(String msg) {
        System.err.println("ERROR: " + msg);
        System.err.println("Type '--help' for usage.");
    }

    /**
     * Prints error and help.
     *
     * @param msg Error message.
     * @param t Throwable, possibly {@code null}.
     */
    static void errorHelp(String msg, Throwable t) {
        errorHelp(msg);

        if (t != null)
            t.printStackTrace();
    }

    /**
     *
     */
    private static class PlotData {
        /** */
        private final PlotSeries series;

        /** */
        private final String plotName;

        /** */
        private final String xAxisLabel;

        /** */
        private final String yAxisLabel;

        /**
         * @param plotName Plot name.
         * @param series Series.
         * @param xAxisLabel X axis label.
         * @param yAxisLabel Y axis label.
         */
        PlotData(String plotName, PlotSeries series, String xAxisLabel, String yAxisLabel) {
            this.plotName = plotName;
            this.series = series;
            this.xAxisLabel = xAxisLabel;
            this.yAxisLabel = yAxisLabel;
        }

        /**
         * @return Series.
         */
        public PlotSeries series() {
            return series;
        }

        /**
         * @return Plot name.
         */
        public String plotName() {
            return plotName;
        }
    }

    /**
     *
     */
    private static class PlotSeries {
        /** */
        private final String seriesName;

        /** */
        private List<double[]> rawData = new ArrayList<>();

        /** */
        private double[][] data;

        /**
         * @param seriesName Series name.
         */
        PlotSeries(String seriesName) {
            this.seriesName = seriesName;
        }

        /**
         *
         */
        public void finish() {
            data = new double[2][];

            data[0] = new double[rawData.size()];
            data[1] = new double[rawData.size()];

            for (int i = 0; i < rawData.size(); i++) {
                double[] tup = rawData.get(i);

                data[0][i] = tup[0];
                data[1][i] = tup[1];
            }

            // No need raw data anymore.
            rawData = null;
        }
    }
}