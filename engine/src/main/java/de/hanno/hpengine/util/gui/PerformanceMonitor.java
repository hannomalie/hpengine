package de.hanno.hpengine.util.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.Timer;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.RenderManager;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;

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
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
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

	private RenderManager renderManager;

	private TimeSeries thirtyFPS;
	private TimeSeries sixtyFPS;
	private TimeSeries actualMS;
	private TimeSeries actualCycleMS;
	private TimeSeries actualSyncTimeMS;

	private DefaultCategoryDataset breakdownDataset;

	private JFrame frame;
	private Engine engine;

	@SuppressWarnings("deprecation")
	public PerformanceMonitor(Engine engine) {
		this.engine = engine;
		this.renderManager = engine.getRenderManager();
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
			this.actualMS = new TimeSeries("Render FPS", Millisecond.class);
			this.actualMS.setMaximumItemAge(maxAge);
			this.actualCycleMS = new TimeSeries("Update FPS", Millisecond.class);
			this.actualCycleMS.setMaximumItemAge(maxAge);
			this.actualSyncTimeMS = new TimeSeries("Gpu Sync", Millisecond.class);
			this.actualSyncTimeMS.setMaximumItemAge(maxAge);
			ChartPanel chartPanel = addFPSChart(renderManager);
			rows++;
			
//			ChartPanel waterfallChartPanel = addBreakdownChart(renderManager);
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

	private ChartPanel addFPSChart(RenderManager myRenderer) {
		TimeSeriesCollection dataset = new TimeSeriesCollection();
		dataset.addSeries(this.actualSyncTimeMS);
		dataset.addSeries(this.actualMS);
		dataset.addSeries(this.actualCycleMS);
		dataset.addSeries(this.thirtyFPS);
		dataset.addSeries(this.sixtyFPS);
		DateAxis domain = new DateAxis("Time");
		NumberAxis range = new NumberAxis("fps");
		domain.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		range.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		domain.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));
		range.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));
		XYItemRenderer renderer = new XYAreaRenderer();
		renderer.setSeriesPaint(0, Color.orange);
		renderer.setSeriesPaint(1, Color.WHITE);
		renderer.setSeriesPaint(2, Color.red);
		renderer.setSeriesPaint(3, Color.DARK_GRAY.brighter().brighter());
		renderer.setSeriesPaint(4, Color.green);
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

	private ChartPanel addBreakdownChart() {
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
        barrenderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator("{1}", decimalformat1));
        barrenderer.setBaseItemLabelsVisible(true);
        ChartPanel waterfallChartPanel = new ChartPanel(jfreechart);
		new BreakdownDataGenerator(100).start();
		return waterfallChartPanel;
	}

	private void thirtyFPS(double y) {
		this.thirtyFPS.addOrUpdate(new Millisecond(), y);
	}

	private void sixtyFPS(double y) {
		this.sixtyFPS.addOrUpdate(new Millisecond(), y);
	}

	private void actualFPS(double y) {
		this.actualMS.addOrUpdate(new Millisecond(), y);
	}
	private void actualCPS(double y) {
		this.actualCycleMS.addOrUpdate(new Millisecond(), y);
	}private void actualSyncTime(double y) {
		this.actualSyncTimeMS.addOrUpdate(new Millisecond(), y);
	}

	class DataGenerator extends Timer implements ActionListener {
		private RenderManager renderer;

		DataGenerator(int interval, RenderManager renderer) {
			super(interval, null);
			addActionListener(this);
			this.renderer = renderer;
		}

		public void actionPerformed(ActionEvent event) {
			long actualFpsValue = (long) renderer.getCurrentFPS();
			long actualCpsValue = (long) engine.getUpdateThread().getFpsCounter().getFPS();
			long syncTimeMS = TimeUnit.NANOSECONDS.toMillis(engine.getRenderManager().getCpuGpuSyncTimeNs());
			double actualSyncTimeFps = syncTimeMS == 0 ? 0 : 1000d/syncTimeMS;
			thirtyFPS(30);
			sixtyFPS(60);
			actualFPS(actualFpsValue);
			actualCPS(Math.min(actualCpsValue, 500));
			actualSyncTime(actualSyncTimeFps);
		}
	}
	
	class BreakdownDataGenerator extends Timer implements ActionListener {

		BreakdownDataGenerator(int interval) {
			super(interval, null);
			addActionListener(this);
		}

		public void actionPerformed(ActionEvent event) {
			Map<String, GPUProfiler.AverageHelper> averages = GPUProfiler.calculateAverages(1000);
			actualizeBreakdownDataset(averages);
		}

		private void actualizeBreakdownDataset(
				Map<String, GPUProfiler.AverageHelper> averages) {
			GPUProfiler.AverageHelper frameAverage = averages.get("Frame");
			if(frameAverage != null) { averages.remove(frameAverage); }
			
			if(breakdownDataset.getColumnKeys().contains("Frame")) {breakdownDataset.removeColumn("Frame");}
			List<String> columnKeys = breakdownDataset.getColumnKeys();
			
			for (String name : averages.keySet()) {
				GPUProfiler.AverageHelper average = averages.get(name);
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
