package jforex.strategies;

import java.util.Arrays;

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
        
    public int macdFastPeriod = 9;
    public int macdSlowPeriod = 12;
    public int macdSignalPeriod = 26;
    public AppliedPrice appliedPrice = AppliedPrice.MEDIAN_PRICE;
    
    public int fastPeriod = 12;
    public int slowPeriod = 26;
    public int signalPeriod = 9;


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
            double[] stochastic = indicators.stoch(instrument, this.period, side, fastKPeriod, slowKPeriod , slowKMaType, 
                    slowDPeriod, slowDMaType, shift);
            print("STO params are "+"Instrument: " + instrument + ", Period: " + this.period + ", Side: " + side + ", FastKPeriod: " + fastKPeriod + ", SlowKPeriod: " + slowKPeriod + ", SlowKMaType: " + slowKMaType + ", SlowDPeriod: " + slowDPeriod + ", SlowDMaType: " + slowDMaType + ", Shift: " + shift);
            
            double[] macd = indicators.macd(instrument, this.period, side, appliedPrice,
              fastPeriod, slowPeriod, signalPeriod, shift);
            
            StringBuilder macdstr = new StringBuilder("macd values are ");
            for (double element : macd) {
                element = element * Math.pow(10, 7);
                macdstr.append(String.format("%.1f", element)).append(" ");
            }
            
            StringBuilder stostr = new StringBuilder("sto values are ");
            for (double element : stochastic) {
                stostr.append(String.format("%.1f", element)).append(" ");
            }
            
            print(stostr.toString());
            print(macdstr.toString());            
        }
    }

    private void createOrderOnStochastic(Instrument instrument, IBar bidBar, double[] stochastic) throws JFException {
        OrderCommand orderCommand;
        if ((stochastic[0] >= 80) && (stochastic[1] >= 80)) {
            orderCommand = OrderCommand.SELL;
            closeOppositeIfExist(orderCommand);
            createOrder(instrument, bidBar, orderCommand);
        } else if ((stochastic[0] <= 20) && (stochastic[1] <= 20) ) {
            orderCommand = OrderCommand.BUY;
            closeOppositeIfExist(orderCommand);
            createOrder(instrument, bidBar, orderCommand);
        }
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
