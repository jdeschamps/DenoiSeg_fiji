package de.csbdresden.denoiseg.predict;


import net.imagej.DatasetService;
import net.imagej.modelzoo.ModelZooArchive;
import net.imagej.modelzoo.consumer.ModelZooPredictionOptions;
import net.imagej.modelzoo.consumer.model.prediction.ImageInput;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import org.scijava.Context;
import org.scijava.plugin.Parameter;

import java.util.Iterator;
import java.util.List;

public class ThresholdOptimizer<T extends RealType<T> & NativeType<T>> {

    @Parameter
    private Context context;

    @Parameter
    private DatasetService datasetService;

    private ModelZooArchive archive;


    private List<Pair<RandomAccessibleInterval<T>, RandomAccessibleInterval<IntType>>> data;

    public ThresholdOptimizer(Context context, ModelZooArchive archive, List<Pair<RandomAccessibleInterval<T>, RandomAccessibleInterval<IntType>>> data){
        context.inject(this);

        this.archive = archive;
        this.data = data;
    }

    public void run() throws Exception {

        // create prediction
        DenoiSegPrediction prediction;
        if (archive.getSpecification().getFormatVersion().compareTo("0.3.0") < 0) {
            prediction = new DeprecatedDenoiSegPrediction(context);
        } else {
            prediction = new DenoiSegPrediction(context);
        }

        prediction.setTrainedModel(archive);
        prediction.setOptions(this.createOptions());

        int n = data.size();
        for(double th=0.1; th<=1.; th+=0.1) {

            double average_score = 0;

            DenoiSegOutput<?, ?> output = singleImagePrediction(prediction, data.get(0).getA());
            final RandomAccessibleInterval<FloatType> seg = (RandomAccessibleInterval<FloatType>) Views.hyperSlice(output.getSegmented(), 2,1);

            final double threshold = th;
            final RandomAccessibleInterval< BoolType > mask = Converters.convert(
                    seg, ( i, o ) -> o.set( i.get() > threshold ), new BoolType() );

            // Create a String labeling
            final Img< IntType > labelImg = ArrayImgs.ints( Intervals.dimensionsAsLongArray( seg ) );
            final ImgLabeling< String, IntType > labeling = new ImgLabeling<>( labelImg );

            // Label connected components
            final ConnectedComponents.StructuringElement se = ConnectedComponents.StructuringElement.FOUR_CONNECTED;
            final Iterator< String > labelCreator = new Iterator< String >()
            {
                int id = 0;

                @Override
                public boolean hasNext()
                {
                    return true;
                }

                @Override
                public synchronized String next()
                {
                    return "l" + ( id++ );
                }
            };
            ConnectedComponents.labelAllConnectedComponents( mask, labeling, labelCreator, se );

            ImageJFunctions.show( labelImg, "labelImg" );

            

            /*for (int i = 0; i < n; i++) {
                DenoiSegOutput<?, ?> output = singleImagePrediction(prediction, data.get(i).getA());
                RandomAccessibleInterval<?> segmented = output.getSegmented();

                average_score += getPrecision(segmented, data.get(i).getB())/n;
            }*/
        }

        // use converter to booltype
        // connected components

        // for each threshold 0.1 to 0.9
            // for each image
                // load image
                // predict
                // compute mask
                // get score comparing to GT
                // save threshold and score
        // return best score
    }

    private double getPrecision(RandomAccessibleInterval<?> pred, RandomAccessibleInterval<IntType> gt){

        return 0.;
    }

    public DenoiSegOutput<?, ?> singleImagePrediction(DenoiSegPrediction prediction, RandomAccessibleInterval<T> image) throws Exception {
        prediction.setInput(new ImageInput("input", image, "XY"));
        prediction.run();

        return prediction.getOutput();
    }

    private ModelZooPredictionOptions createOptions() {
        return ModelZooPredictionOptions.options().numberOfTiles(1).batchSize(10).showProgressDialog(false).convertIntoInputFormat(false);
    }

}
