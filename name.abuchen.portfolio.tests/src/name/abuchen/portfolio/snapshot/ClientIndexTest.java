package name.abuchen.portfolio.snapshot;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import name.abuchen.portfolio.AccountBuilder;
import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.PortfolioBuilder;
import name.abuchen.portfolio.SecurityBuilder;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Values;
import name.abuchen.portfolio.util.Dates;

import org.hamcrest.number.IsCloseTo;
import org.joda.time.DateMidnight;
import org.junit.Test;

@SuppressWarnings("nls")
public class ClientIndexTest
{
    private static final double PRECISION = 0.000001d;

    private Client createClient()
    {
        Client client = new Client();

        new AccountBuilder() //
                        .deposit_("2011-12-31", 10000 * Values.Amount.factor()) //
                        .interest("2012-01-01", 230 * Values.Amount.factor()) //
                        .deposit_("2012-01-02", 200 * Values.Amount.factor()) //
                        .interest("2012-01-02", 200 * Values.Amount.factor()) //
                        .withdraw("2012-01-03", 400 * Values.Amount.factor()) //
                        .fees____("2012-01-03", 23441) //
                        .interest("2012-01-04", 29399) //
                        .interest("2012-01-05", 29399) //
                        .deposit_("2012-01-06", 5400 * Values.Amount.factor()) //
                        .interest("2012-01-06", 19599) //
                        .withdraw("2012-01-07", 369704) //
                        .fees____("2012-01-07", 88252) //
                        .fees____("2012-01-08", 100385) //
                        .addTo(client);

        return client;
    }

    @Test
    public void testExcelSample()
    {
        Client client = createClient();

        ReportingPeriod.FromXtoY period = new ReportingPeriod.FromXtoY(Dates.date(2011, Calendar.DECEMBER, 31), //
                        Dates.date(2012, Calendar.JANUARY, 8));
        ClientIndex index = PerformanceIndex.forClient(client, period, new ArrayList<Exception>());

        assertNotNull(index);

        assertThat(period.toInterval(), is(index.getReportInterval().toInterval()));
        assertThat(client, is(index.getClient()));

        Date[] dates = index.getDates();
        assertThat(dates.length, is(Dates.daysBetween(period.getStartDate(), period.getEndDate()) + 1));

        double[] delta = index.getDeltaPercentage();
        assertThat(delta[0], is(0d));
        assertThat(delta[1], IsCloseTo.closeTo(0.023d, PRECISION));
        assertThat(delta[2], IsCloseTo.closeTo(0.0195503d, PRECISION));
        assertThat(delta[3], IsCloseTo.closeTo(-0.0220517d, PRECISION));
        assertThat(delta[4], IsCloseTo.closeTo(0.0294117647d, PRECISION));
        assertThat(delta[5], IsCloseTo.closeTo(0.0285714286d, PRECISION));
        assertThat(delta[6], IsCloseTo.closeTo(0.0185185185d, PRECISION));
        assertThat(delta[7], IsCloseTo.closeTo(-0.0545454545d, PRECISION));
        assertThat(delta[8], IsCloseTo.closeTo(-0.0865384615d, PRECISION));

        double[] accumulated = index.getAccumulatedPercentage();
        assertThat(accumulated[0], is(0d));
        assertThat(accumulated[1], IsCloseTo.closeTo(0.023d, PRECISION));
        assertThat(accumulated[2], IsCloseTo.closeTo(0.043d, PRECISION));
        assertThat(accumulated[3], IsCloseTo.closeTo(0.02d, PRECISION));
        assertThat(accumulated[4], IsCloseTo.closeTo(0.05d, PRECISION));
        assertThat(accumulated[5], IsCloseTo.closeTo(0.08d, PRECISION));
        assertThat(accumulated[6], IsCloseTo.closeTo(0.10d, PRECISION));
        assertThat(accumulated[7], IsCloseTo.closeTo(0.04d, PRECISION));
        assertThat(accumulated[8], IsCloseTo.closeTo(-0.05d, PRECISION));
    }

    private Client createClient(double[] delta, long[] transferals)
    {
        Client client = new Client();

        AccountBuilder account = new AccountBuilder();

        DateMidnight time = new DateMidnight("2012-01-01");

        long valuation = 0;
        double quote = 1;
        for (int ii = 0; ii < delta.length; ii++)
        {
            long v = (long) Math.round((double) valuation * (delta[ii] + 1) / quote);
            long d = v - valuation;

            if (transferals[ii] > 0)
                account.deposit_(time, transferals[ii]);
            else if (transferals[ii] < 0)
                account.withdraw(time, Math.abs(transferals[ii]));

            if (v > 0)
                account.interest(time, d);
            else if (v < 0)
                account.fees____(time, Math.abs(d));

            valuation = v + transferals[ii];

            quote = 1 + delta[ii];

            time = time.plusDays(1);
        }

        account.addTo(client);
        return client;
    }

    @Test
    public void testThatTransferalsDoNotChangePerformance()
    {
        double[] delta = { 0d, 0.023d, 0.043d, 0.02d, 0.05d, 0.08d, 0.1d, 0.04d, -0.05d };
        long[] transferals = { 1000000, 0, 20000, -40000, 0, 0, 540000, -369704, 0 };
        long[] transferals2 = { 1000000, 0, 0, 0, 0, 0, 0, 0, 0 };

        Client client = createClient(delta, transferals);

        ReportingPeriod.FromXtoY period = new ReportingPeriod.FromXtoY(Dates.date(2012, Calendar.JANUARY, 1), //
                        Dates.date(2012, Calendar.JANUARY, 9));
        ClientIndex index = PerformanceIndex.forClient(client, period, new ArrayList<Exception>());

        double[] accumulated = index.getAccumulatedPercentage();
        for (int ii = 0; ii < accumulated.length; ii++)
            assertThat(accumulated[ii], IsCloseTo.closeTo(delta[ii], PRECISION));

        Client anotherClient = createClient(delta, transferals2);
        index = PerformanceIndex.forClient(anotherClient, period, new ArrayList<Exception>());

        accumulated = index.getAccumulatedPercentage();
        for (int ii = 0; ii < accumulated.length; ii++)
            assertThat(accumulated[ii], IsCloseTo.closeTo(delta[ii], PRECISION));
    }

    @Test
    public void testThatNoValuationResultsInZeroPerformance()
    {
        Client client = new Client();

        ReportingPeriod.FromXtoY period = new ReportingPeriod.FromXtoY(Dates.date(2012, Calendar.JANUARY, 1), //
                        Dates.date(2012, Calendar.JANUARY, 9));
        ClientIndex index = PerformanceIndex.forClient(client, period, new ArrayList<Exception>());

        double[] accumulated = index.getAccumulatedPercentage();
        for (int ii = 0; ii < accumulated.length; ii++)
            assertThat(accumulated[ii], IsCloseTo.closeTo(0d, PRECISION));
    }

    @Test
    public void testThatInterstWithoutInvestmentDoesNotCorruptResultAndIsReported()
    {
        Client client = new Client();
        new AccountBuilder() //
                        .interest("2012-01-02", 100) //
                        .addTo(client);

        ReportingPeriod.FromXtoY period = new ReportingPeriod.FromXtoY(Dates.date(2012, Calendar.JANUARY, 1), //
                        Dates.date(2012, Calendar.JANUARY, 9));

        List<Exception> errors = new ArrayList<Exception>();
        ClientIndex index = PerformanceIndex.forClient(client, period, errors);

        double[] accumulated = index.getAccumulatedPercentage();
        for (int ii = 0; ii < accumulated.length; ii++)
            assertThat(accumulated[ii], IsCloseTo.closeTo(0d, PRECISION));

        assertThat(errors.size(), is(1));
        assertThat(errors.get(0).getMessage(),
                        startsWith(Messages.MsgDeltaWithoutAssets.substring(0,
                                        Messages.MsgDeltaWithoutAssets.indexOf('{'))));
    }

    @Test
    public void testFirstDataPointWhenInsideReportingInterval()
    {
        Client client = createClient();

        ReportingPeriod.FromXtoY period = new ReportingPeriod.FromXtoY(Dates.date(2011, Calendar.DECEMBER, 20), //
                        Dates.date(2012, Calendar.JANUARY, 8));
        ClientIndex index = PerformanceIndex.forClient(client, period, new ArrayList<Exception>());

        assertThat(index.getFirstDataPoint(), is(new DateMidnight(2011, 12, 31).toDateTime()));
        assertThat(index.getFirstDataPoint(), not(period.toInterval().getStart()));
    }

    @Test
    public void testThatPerformanceOfAnInvestmentIntoAnIndexIsIdenticalToIndex()
    {
        DateMidnight startDate = new DateMidnight(2012, 1, 1);
        DateMidnight endDate = new DateMidnight(2012, 4, 29); // a weekend
        long startPrice = 100 * Values.Amount.factor();

        Client client = new Client();

        Security security = new SecurityBuilder() //
                        .generatePrices(startPrice, startDate, endDate) //
                        .addTo(client);

        PortfolioBuilder portfolio = new PortfolioBuilder();

        // add some buy transactions
        DateMidnight date = startDate;
        while (date.isBefore(endDate))
        {
            long p = security.getSecurityPrice(date.toDate()).getValue();
            portfolio.inbound_delivery(security, date, 1 * Values.Share.factor(), p);
            date = date.plusDays(20);
        }

        portfolio.addTo(client);

        ReportingPeriod.FromXtoY period = new ReportingPeriod.FromXtoY(startDate.toDate(), endDate.toDate());

        List<Exception> warnings = new ArrayList<Exception>();
        ClientIndex index = PerformanceIndex.forClient(client, period, warnings);
        assertTrue(warnings.isEmpty());

        double[] accumulated = index.getAccumulatedPercentage();
        long lastPrice = security.getSecurityPrice(endDate.toDate()).getValue();

        assertThat((double) (lastPrice - startPrice) / (double) startPrice,
                        IsCloseTo.closeTo(accumulated[accumulated.length - 1], PRECISION));
    }

    @Test
    public void testExcelSampleAggregatedWeekly()
    {
        Client client = createClient();

        ReportingPeriod.FromXtoY reportInterval = new ReportingPeriod.FromXtoY(Dates.date(2011, Calendar.DECEMBER, 31), //
                        Dates.date(2012, Calendar.JANUARY, 8));
        PerformanceIndex index = PerformanceIndex.forClient(client, reportInterval, new ArrayList<Exception>());

        index = Aggregation.aggregate(index, Aggregation.Period.WEEKLY);

        assertNotNull(index);

        double[] delta = index.getDeltaPercentage();
        assertThat(delta.length, is(2));
        assertThat(delta[0], IsCloseTo.closeTo(0.023d, PRECISION));
        assertThat(delta[1], IsCloseTo.closeTo(-0.0713587d, PRECISION));

        double[] accumulated = index.getAccumulatedPercentage();
        assertThat(accumulated[0], IsCloseTo.closeTo(0.023d, PRECISION));
        assertThat(accumulated[1], IsCloseTo.closeTo(-0.05d, PRECISION));
    }

    @Test
    public void testThatDepositsOnTheLastDayArePerformanceNeutral()
    {
        Client client = new Client();

        new AccountBuilder() //
                        .deposit_("2012-01-01", 10000) //
                        .interest("2012-01-02", 1000) //
                        .deposit_("2012-01-10", 10000) //
                        .addTo(client);

        ReportingPeriod.FromXtoY reportInterval = new ReportingPeriod.FromXtoY(Dates.date(2012, Calendar.JANUARY, 1), //
                        Dates.date(2012, Calendar.JANUARY, 10));
        PerformanceIndex index = PerformanceIndex.forClient(client, reportInterval, new ArrayList<Exception>());

        double[] accumulated = index.getAccumulatedPercentage();
        assertThat(accumulated[accumulated.length - 2], IsCloseTo.closeTo(0.1d, PRECISION));
        assertThat(accumulated[accumulated.length - 1], IsCloseTo.closeTo(0.1d, PRECISION));
    }

    @Test
    public void testChangesOnFirstDayOfInvestment()
    {
        Client client = new Client();

        new AccountBuilder() //
                        .deposit_("2012-01-01", 10000) //
                        .interest("2012-01-02", 1000) //
                        .addTo(client);

        ReportingPeriod.FromXtoY reportInterval = new ReportingPeriod.FromXtoY(Dates.date(2012, Calendar.JANUARY, 1), //
                        Dates.date(2012, Calendar.JANUARY, 10));
        PerformanceIndex index = PerformanceIndex.forClient(client, reportInterval, new ArrayList<Exception>());

        double[] accumulated = index.getAccumulatedPercentage();
        assertThat(accumulated[accumulated.length - 1], IsCloseTo.closeTo(0.1d, PRECISION));
    }

}
