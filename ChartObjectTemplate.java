package jforex;

import java.awt.Color;

import com.dukascopy.api.*;
import com.dukascopy.api.drawings.*;

/**
 * The following strategy is a template to be used with snippets
 * from Chart object catalog
 *
 */
public class ChartObjectTemplate implements IStrategy {

    private IChart chart;
    private IHistory history;
    private IConsole console;
    
    @Configurable("")
    public Instrument instrument = Instrument.EURUSD;

    private OfferSide offerSide;
    private Period period;
    private IChartObjectFactory factory;     

    
    @Override
    public void onStart(IContext context) throws JFException {
        history = context.getHistory();
        console = context.getConsole();
        chart = context.getChart(instrument);
        
        if(chart == null){
            console.getErr().println("No chart opened for " + instrument);
            context.stop(); //stop the strategy
        }
        period = chart.getSelectedPeriod();
        offerSide = chart.getSelectedOfferSide();
        factory = chart.getChartObjectFactory();
    }

    @Override
    public void onTick(Instrument instrument, ITick tick) throws JFException {    }

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        // Check if the period of the incoming bar matches the desired period
        if (period.equals(this.period)) {
            IBar bar1 = history.getBar(instrument, this.period, offerSide, 1);

            // Create unique keys for each signal
            String signalUpKey = "signalUpKey" + bar1.getTime();
            String signalDownKey = "signalDownKey" + bar1.getTime();

            // Check if the signals already exist for this bar
            if (chart.get(signalUpKey) == null) {
                ISignalUpChartObject signalUp = factory.createSignalUp(signalUpKey,
                    bar1.getTime(), bar1.getLow()
                );
                chart.add(signalUp);
            }
            
            if (chart.get(signalDownKey) == null) {
                ISignalDownChartObject signalDown = factory.createSignalDown(signalDownKey,
                bar1.getTime(), bar1.getHigh()
                );
                chart.add(signalDown);
            }
        }
    }


    @Override
    public void onMessage(IMessage message) throws JFException {}

    @Override
    public void onAccount(IAccount account) throws JFException {}

    @Override
    public void onStop() throws JFException {}

}
