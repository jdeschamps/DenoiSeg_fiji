package de.csbdresden.denoiseg.predict;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;

import java.util.Iterator;

public class DenoiSegPostProcessor {

    public static ImgLabeling<String, IntType> process(RandomAccessibleInterval<FloatType> seg, double threshold){
        final RandomAccessibleInterval<BoolType> mask = Converters.convert(
                seg, (i, o) -> o.set(i.get() > threshold), new BoolType());

        // create a String labeling
        final Img<IntType> labelImg = ArrayImgs.ints(Intervals.dimensionsAsLongArray(seg));
        final ImgLabeling<String, IntType> labeling = new ImgLabeling<>(labelImg);

        // label connected components
        final ConnectedComponents.StructuringElement se = ConnectedComponents.StructuringElement.FOUR_CONNECTED;
        final Iterator<String> labelCreator = new Iterator<String>() {
            int id = 0;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public synchronized String next() {
                return "l" + (id++);
            }
        };
        ConnectedComponents.labelAllConnectedComponents(mask, labeling, labelCreator, se);

        return labeling;
    }
}
