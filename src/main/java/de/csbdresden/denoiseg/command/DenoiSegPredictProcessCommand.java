package de.csbdresden.denoiseg.command;

import de.csbdresden.denoiseg.predict.DenoiSegOutput;
import de.csbdresden.denoiseg.predict.DenoiSegPostProcessor;
import de.csbdresden.denoiseg.predict.DenoiSegPrediction;
import de.csbdresden.denoiseg.predict.DeprecatedDenoiSegPrediction;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.modelzoo.consumer.command.AbstractSingleImagePredictionCommand;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.IOException;
import java.util.Iterator;

@Plugin( type = Command.class, menuPath = "Plugins>CSBDeep>DenoiSeg>DenoiSeg predict + post-process" )
public class DenoiSegPredictProcessCommand <T extends RealType<T> & NativeType<T>> extends AbstractSingleImagePredictionCommand<T, DenoiSegPrediction> {

    @Parameter(label = "Threshold", min = "0.1", max = "0.9", stepSize = "0.1")
    private double threshold = 0.1;

    @Parameter(type = ItemIO.OUTPUT)
    private Dataset denoised;

    @Parameter(type = ItemIO.OUTPUT)
    private Dataset segmented;

    @Parameter(type = ItemIO.OUTPUT)
    private Dataset processed;

    @Parameter
    private DatasetService datasetService;

    @Override
    protected DenoiSegPrediction createPrediction() {
        try {
            if(getArchive().getSpecification().getFormatVersion().compareTo("0.3.0") < 0) {
                return new DeprecatedDenoiSegPrediction(getContext());
            } else {
                return new DenoiSegPrediction(getContext());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void createOutput(DenoiSegPrediction prediction) {
        DenoiSegOutput<?, ?> output = prediction.getOutput();
        denoised = datasetService.create(output.getDenoised());
        segmented = datasetService.create(output.getSegmented());
        segmented.setRGBMerged(false);
    }

    @Override
    public void run() {
        super.run();

        if(segmented != null) {
            int[] dims = Intervals.dimensionsAsIntArray(segmented);
            int dimToFix = 0;
            for (int i = 0; i < dims.length; i++) {
                if (dims[i] == 3) dimToFix = i;
            }
            final RandomAccessibleInterval<FloatType> seg = (RandomAccessibleInterval<FloatType>) Views.hyperSlice(segmented.getImgPlus(), dimToFix, 1);

            final Img<IntType> labelImg = DenoiSegPostProcessor.process(seg, threshold);
            processed = datasetService.create(labelImg);
        }
    }

    public static String getOutputSegmentedName() {
        return "segmented";
    }

    public static String getOutputDenoisedName() {
        return "denoised";
    }
}
