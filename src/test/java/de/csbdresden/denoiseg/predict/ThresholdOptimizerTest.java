package de.csbdresden.denoiseg.predict;

import de.csbdresden.denoiseg.threshold.ThresholdOptimizer;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ThresholdOptimizerTest {

    // test empty image -> Nan?


    @Test
    public void testPrecisionIdentical(){
        long[] dims = {64,64};
        final Img<IntType> img = ArrayImgs.ints( dims );

        // paint
        long[] interval = {12, 28, 42, 56};
        IntervalView<IntType> intView = Views.interval(img, Intervals.createMinMax(interval));
        Cursor<IntType> cur = intView.cursor();
        while(cur.hasNext()){
            cur.next().set(9);
        }

        long[] interval2 = {43, 9, 52, 18};
        IntervalView<IntType> intView2 = Views.interval(img, Intervals.createMinMax(interval2));
        Cursor<IntType> cur2 = intView2.cursor();
        while(cur2.hasNext()){
            cur2.next().set(12);
        }

        assertEquals(1., ThresholdOptimizer.getPrecision(img, img), 0.0001);
    }
    @Test
    public void testPrecisionAgainstEmpty(){
        long[] dims = {64,64};
        final Img<IntType> img = ArrayImgs.ints( dims );
        final Img<IntType> img2 = ArrayImgs.ints( dims );

        // paint
        long[] interval = {12, 28, 42, 56};
        IntervalView<IntType> intView = Views.interval(img, Intervals.createMinMax(interval));
        Cursor<IntType> cur = intView.cursor();
        while(cur.hasNext()){
            cur.next().set(9);
        }

        assertEquals(0., ThresholdOptimizer.getPrecision(img, img2), 0.0001);
    }

    @Test
    public void testPrecisionNonOverlapping(){
        long[] dims = {64,64};
        final Img<IntType> img = ArrayImgs.ints( dims );
        final Img<IntType> img2 = ArrayImgs.ints( dims );

        // paint
        long[] interval = {12, 15, 22, 26};
        IntervalView<IntType> intView = Views.interval(img, Intervals.createMinMax(interval));
        Cursor<IntType> cur = intView.cursor();
        while(cur.hasNext()){
            cur.next().set(9);
        }

        long[] interval2 = {39, 49, 50, 55};
        IntervalView<IntType> intView2 = Views.interval(img2, Intervals.createMinMax(interval2));
        Cursor<IntType> cur2 = intView2.cursor();
        while(cur2.hasNext()){
            cur2.next().set(12);
        }

        assertEquals(0., ThresholdOptimizer.getPrecision(img, img2), 0.0001);
    }

    @Test
    public void testPrecision(){
        long[] dims = {16,16};
        final Img<IntType> img = ArrayImgs.ints( dims );
        final Img<IntType> img2 = ArrayImgs.ints( dims );

        // paint
        long[] interval = {2, 2, 11, 11};
        IntervalView<IntType> intView = Views.interval(img, Intervals.createMinMax(interval));
        Cursor<IntType> cur = intView.cursor();
        while(cur.hasNext()){
            cur.next().set(9);
        }

        long[] interval2 = {3, 3, 12, 12};
        IntervalView<IntType> intView2 = Views.interval(img2, Intervals.createMinMax(interval2));
        Cursor<IntType> cur2 = intView2.cursor();
        while(cur2.hasNext()){
            cur2.next().set(12);
        }

        assertEquals((100.-19.)/119., ThresholdOptimizer.getPrecision(img, img2), 0.0001);
    }
}
