package util.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.Timer;

import renderer.Renderer;
import util.stopwatch.GPUProfiler;
import util.stopwatch.GPUProfiler.AverageHelper;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.ui.RectangleInsets;

/*
 * Thank you David Gilbert ! All source code is from him, I just modified it.
 * http://www.jfree.org/jfreechart/api/javadoc/src-html/org/jfree/chart/demo/PieChartDemo1.html
 * */

public class PerformanceMonitor {

	private Renderer myRenderer;

	private TimeSeries thirtyFPS;
	private TimeSeries sixtyFPS;
	private TimeSeries actualMS;

	private DefaultCategoryDataset breakdownDataset;

	private JFrame frame;

	@SuppressWarnings("deprecation")
	public PerformanceMonitor(Renderer myRenderer) {
		this.myRenderer = myRenderer;
	}

	public void init() {
		long maxAge = 10000;
		if(frame == null) {
			frame = new JFrame("FPS");

			int rows = 0;
			
			this.thirtyFPS = new TimeSeries("30 FPS", Millisecond.class);
			this.thirtyFPS.setMaximumItemAge(maxAge);
			this.sixtyFPS = new TimeSeries("60 FPS", Millisecond.class);
			this.sixtyFPS.setMaximumItemAge(maxAge);
			this.actualMS = new TimeSeries("Current FPS", Millisecond.class);
			this.actualMS.setMaximumItemAge(maxAge);
			ChartPanel chartPanel = addFPSChart(myRenderer);
			rows++;
			
//			ChartPanel waterfallChartPanel = addBreakdownChart(myRenderer);
//			rows++;

			frame.setLayout(new GridLayout(rows, 1));
			frame.add(chartPanel);
//			frame.add(waterfallChartPanel);
			frame.pack();
		}
		frame.setVisible(false);
	}

	public void toggleVisibility() {
		frame.setVisible(!frame.isVisible());
	}

	private ChartPanel addFPSChart(Renderer myRenderer) {
		TimeSeriesCollection dataset = new TimeSeriesCollection();
		dataset.addSeries(this.thirtyFPS);
		dataset.addSeries(this.sixtyFPS);
		dataset.addSeries(this.actualMS);
		DateAxis domain = new DateAxis("Time");
		NumberAxis range = new NumberAxis("fps");
		domain.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		range.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		domain.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));
		range.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));
		XYItemRenderer renderer = new XYLineAndShapeRenderer(true, false);
		renderer.setSeriesPaint(0, Color.red);
		renderer.setSeriesPaint(1, Color.green);
		renderer.setSeriesPaint(2, Color.blue);
		renderer.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
		XYPlot plot = new XYPlot(dataset, domain, range, renderer);
		plot.setBackgroundPaint(Color.lightGray);
		plot.setDomainGridlinePaint(Color.white);
		plot.setRangeGridlinePaint(Color.white);
		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		domain.setAutoRange(true);
		domain.setLowerMargin(0.0);
		domain.setUpperMargin(0.0);
		domain.setTickLabelsVisible(true);
		range.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
		JFreeChart chart = new JFreeChart("FPS Monitor", new Font("SansSerif", Font.BOLD, 24), plot, true);
		chart.setBackgroundPaint(Color.white);
		ChartPanel chartPanel = new ChartPanel(chart);
		chartPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createEmptyBorder(4, 4, 4, 4),
				BorderFactory.createLineBorder(Color.black)));
		new DataGenerator(100, myRenderer).start();
		return chartPanel;
	}

	private ChartPanel addBreakdownChart(Renderer myRenderer) {
		// Code from https://code.google.com/p/swing-ui-hxzon/source/browse/trunk/jfreechart/org/jfree/chart/demo/WaterfallChartDemo1.java?spec=svn65&r=62
		breakdownDataset = new DefaultCategoryDataset();
        JFreeChart jfreechart = ChartFactory.createWaterfallChart("Frame Breakdown", "part", "ms", breakdownDataset, PlotOrientation.VERTICAL, false, true, false);
		jfreechart.setBackgroundPaint(Color.white);
        CategoryPlot categoryplot = (CategoryPlot) jfreechart.getPlot();
        categoryplot.setBackgroundPaint(Color.lightGray);
        categoryplot.setRangeGridlinePaint(Color.white);
        categoryplot.setRangeGridlinesVisible(true);
        categoryplot.setAxisOffset(new RectangleInsets(5D, 5D, 5D, 5D));
        ValueAxis valueaxis = categoryplot.getRangeAxis();
        DecimalFormat decimalformat = new DecimalFormat("##,#");
//        decimalformat.setNegativePrefix("(");
//        decimalformat.setNegativeSuffix(")");
        TickUnits tickunits = new TickUnits();
        tickunits.add(new NumberTickUnit(5D, decimalformat));
        tickunits.add(new NumberTickUnit(10D, decimalformat));
        tickunits.add(new NumberTickUnit(20D, decimalformat));
        tickunits.add(new NumberTickUnit(50D, decimalformat));
        tickunits.add(new NumberTickUnit(100D, decimalformat));
        tickunits.add(new NumberTickUnit(200D, decimalformat));
        tickunits.add(new NumberTickUnit(500D, decimalformat));
        tickunits.add(new NumberTickUnit(1000D, decimalformat));
        tickunits.add(new NumberTickUnit(2000D, decimalformat));
        tickunits.add(new NumberTickUnit(5000D, decimalformat));
        valueaxis.setStandardTickUnits(tickunits);
        BarRenderer barrenderer = (BarRenderer) categoryplot.getRenderer();
        barrenderer.setDrawBarOutline(false);
        barrenderer.setBase(5D);
        DecimalFormat decimalformat1 = new DecimalFormat("$##,#");
//        decimalformat1.setNegativePrefix("(");
//        decimalformat1.setNegativeSuffix(")");
        barrenderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator("{1}", decimalformat1));
        barrenderer.setBaseItemLabelsVisible(true);
        ChartPanel waterfallChartPanel = new ChartPanel(jfreechart);
		new BreakdownDataGenerator(100, myRenderer).start();
		return waterfallChartPanel;
	}

	private void thirtyFPS(double y) {
		this.thirtyFPS.addOrUpdate(new Millisecond(), y);
	}

	private void sixtyFPS(double y) {
		this.sixtyFPS.addOrUpdate(new Millisecond(), y);
	}

	private void actualFPS(double y) {
		this.actualMS.add(new Millisecond(), y);
	}

	class DataGenerator extends Timer implements ActionListener {
		private Renderer renderer;

		DataGenerator(int interval, Renderer renderer) {
			super(interval, null);
			addActionListener(this);
			this.renderer = renderer;
		}

		public void actionPerformed(ActionEvent event) {
			long actualFpsMSValue = (long) renderer.getCurrentFPS();
			thirtyFPS(30);
			sixtyFPS(60);
			actualFPS(actualFpsMSValue);
		}
	}
	
	class BreakdownDataGenerator extends Timer implements ActionListener {
		private Renderer renderer;

		BreakdownDataGenerator(int interval, Renderer renderer) {
			super(interval, null);
			addActionListener(this);
			this.renderer = renderer;
		}

		public void actionPerformed(ActionEvent event) {
			Map<String, AverageHelper> averages = GPUProfiler.calculateAverages(1000);
			actualizeBreakdownDataset(averages);
		}

		private void actualizeBreakdownDataset(
				Map<String, AverageHelper> averages) {
			AverageHelper frameAverage = averages.get("Frame");
			if(frameAverage != null) { averages.remove(frameAverage); }
			
			if(breakdownDataset.getColumnKeys().contains("Frame")) {breakdownDataset.removeColumn("Frame");}
			List<String> columnKeys = breakdownDataset.getColumnKeys();
			
			for (String name : averages.keySet()) {
				AverageHelper average = averages.get(name);
				Long averageValue = average.getAverageInMS();
				if(averageValue < 2) { continue; }
				
				if(columnKeys.contains(name)) {
					breakdownDataset.setValue(averageValue, "", name);
				} else {
			        breakdownDataset.addValue(averageValue, "", name);	
				}
			}

			if(frameAverage != null) { breakdownDataset.addValue(frameAverage.getAverageInMS(), "", "Frame"); }
		}
	}

	public JFrame getFrame() {
		return frame;
	}

}
