package main.util.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.Timer;

import main.renderer.Renderer;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
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

	 public PerformanceMonitor(Renderer myRenderer) {
		 this.myRenderer = myRenderer;
		long maxAge = 10000;

		this.thirtyFPS = new TimeSeries("33 FPS", Millisecond.class);
		this.thirtyFPS.setMaximumItemAge(maxAge);
		this.sixtyFPS = new TimeSeries("66 FPS", Millisecond.class);
		this.sixtyFPS.setMaximumItemAge(maxAge);
		this.actualMS = new TimeSeries("Current FPS", Millisecond.class);
		this.actualMS.setMaximumItemAge(maxAge);
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
		JFrame frame = new JFrame();
		frame.getContentPane().add(chartPanel);
		frame.pack();
		frame.setVisible(true);
		new DataGenerator(100, myRenderer).start();
	}

	private void thirtyFPS(double y) {
		this.thirtyFPS.add(new Millisecond(), y);
	}

	private void sixtyFPS(double y) {
		this.sixtyFPS.add(new Millisecond(), y);
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
}
