package jforex.strategies;

import java.util.Arrays;
import java.awt.Color;

import com.dukascopy.api.*;
import com.dukascopy.api.drawings.*;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.*;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IIndicators.MaType;

public class MACD_STOCH implements IStrategy {
    private IEngine engine;
    private IIndicators indicators;
    private IConsole console;
    private IChart chart;
    private IHistory history;
    private IChartObjectFactory factory;   
    private Period period;


    @Configurable("Amount")
    public double amount = 0.2;
    @Configurable("Period")
    public Period fixedPeriod = Period.FIFTEEN_MINS;
    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;

    private OfferSide side;
    private int fastKPeriod = 10;
    private MaType slowDMaType =  MaType.SMMA;
    private int slowKPeriod = 24;
    private MaType slowKMaType =  MaType.TEMA;
    private int slowDPeriod = 45;
    private int shift = 0;
    public int counter = 0;
        
    public int fastPeriod = 12;
    public int slowPeriod = 26;
    public int signalPeriod = 9;
    public AppliedPrice appliedPrice = AppliedPrice.MEDIAN_PRICE;
        
    private boolean stoch_buy = false;
    private boolean stoch_sell = false;
    private boolean macd_buy = false;
    private boolean macd_sell = false;
    private boolean SignalUP = false;
    private boolean SignalDOWN = false;

    public void onStart(IContext context) throws JFException {
        engine = context.getEngine();
        indicators = context.getIndicators();
        history = context.getHistory();
        console = context.getConsole();
        chart = context.getChart(instrument);
        
        if(chart == null){
            console.getErr().println("No chart opened for " + instrument);
            context.stop(); //stop the strategy
        }
        period = chart.getSelectedPeriod();
        side = chart.getSelectedOfferSide();
        factory = chart.getChartObjectFactory();
    }

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (instrument.equals(instrument) && period.equals(fixedPeriod)) {
            //calculate Stoch
            double[] stochastic = indicators.stoch(instrument, this.period, side, fastKPeriod, slowKPeriod , slowKMaType, 
                    slowDPeriod, slowDMaType, shift);
            //print("STO params are "+"Instrument: " + instrument + ", Period: " + this.period + ", Side: " + side + ", FastKPeriod: " + fastKPeriod + ", SlowKPeriod: " + slowKPeriod + ", SlowKMaType: " + slowKMaType + ", SlowDPeriod: " + slowDPeriod + ", SlowDMaType: " + slowDMaType + ", Shift: " + shift);
            
            double[] stochastic_shift1 = indicators.stoch(instrument, this.period, side, fastKPeriod, slowKPeriod , slowKMaType, 
                slowDPeriod, slowDMaType, 1);
            
            // Update stoch_buy and stoch_sell flags
            if (!stoch_buy){
                stoch_buy = (stochastic[0] > stochastic[1] && stochastic_shift1[0] < stochastic_shift1[1]);
            }
            if (!stoch_sell){
                stoch_sell = (stochastic[0] < stochastic[1] && stochastic_shift1[0] > stochastic_shift1[1]);
            }
            
            //calculate MACD
            double[] macd = indicators.macd(instrument, this.period, side, appliedPrice,
                fastPeriod, slowPeriod, signalPeriod, shift);
              
            double[] macd_shift1 = indicators.macd(instrument, this.period, side, appliedPrice,
                fastPeriod, slowPeriod, signalPeriod, 1);

            // Update macd_buy and macd_sell flags
            if (!macd_buy){
                macd_buy = (macd[0] > macd[2] && macd_shift1[0] < macd_shift1[2]);
            }
            if (!macd_sell){
                macd_sell = (macd[0] < macd[2] && macd_shift1[0] > macd_shift1[2]);
            }
            
            // Create unique keys for each signal
            String signalUpKey = "signalUpKey" + bidBar.getTime();
            String signalDownKey = "signalDownKey" + bidBar.getTime();
            
            StringBuilder macdstr = new StringBuilder("macd values are ");
            for (double element : macd) {
                element = element * Math.pow(10, 7);
                macdstr.append(String.format("%.1f", element)).append(" ");
            }
            
            StringBuilder stostr = new StringBuilder("sto values are ");
            for (double element : stochastic) {
                stostr.append(String.format("%.1f", element)).append(" ");
            }
            
            
            // Create arrow up when both stoch_buy and macd_buy are true
            if (stoch_buy && macd_buy && chart.get(signalUpKey) == null) {
                ISignalUpChartObject signalUp = factory.createSignalUp(signalUpKey, bidBar.getTime(), bidBar.getLow());
                signalUp.setColor(Color.GREEN);  // You can set the color of the arrow here
                chart.add(signalUp);
                stoch_buy = false;
                macd_buy = false;
                print(stostr.toString());
                print(macdstr.toString());
                submitOrder(OrderCommand.BUY, instrument, bidBar);
            }

            // Create arrow down when both stoch_sell and macd_sell are true
            if (stoch_sell && macd_sell && chart.get(signalDownKey) == null) {
                ISignalDownChartObject signalDown = factory.createSignalDown(signalDownKey, bidBar.getTime(), bidBar.getHigh());
                signalDown.setColor(Color.RED);  // You can set the color of the arrow here
                chart.add(signalDown);
                stoch_sell = false;
                macd_sell = false;
                print(stostr.toString());
                print(macdstr.toString());
                submitOrder(OrderCommand.SELL, instrument, bidBar);
            }            
        }
    }

    private void submitOrder(OrderCommand orderCommand, Instrument instrument, IBar bidBar) throws JFException {
        if (engine.getOrders().size() > 0) {
            return;
        }
        engine.submitOrder(getLabel(instrument), instrument, orderCommand, amount);
    }

    private void closeOppositeIfExist(OrderCommand command) throws JFException {
        if (engine.getOrders().size() == 0) {
            return;
        }
        for (IOrder order: engine.getOrders(instrument)) {
            if (!order.getOrderCommand().equals(command)) {
                order.close();
            }
        }
    }

    private void createOrder(Instrument instrument, IBar bidBar, OrderCommand orderCommand) throws JFException {
        if (engine.getOrders().size() > 0) {
            return;
        }
        engine.submitOrder(getLabel(instrument), instrument, orderCommand, amount);
    }
    
    

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {

    }

    public void onStop() throws JFException {
        for (IOrder order : engine.getOrders(instrument)) {
            order.close();
        }
    }
    
    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }

    public void print(String string) {
        console.getOut().println(string);
    }
    
    protected String getLabel(Instrument instrument) {
        return (instrument.name() + (counter ++)).toUpperCase();
    }
}
