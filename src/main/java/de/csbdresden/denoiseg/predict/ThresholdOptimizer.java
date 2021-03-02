package de.csbdresden.denoiseg.predict;


import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import net.imagej.DatasetService;
import net.imagej.modelzoo.ModelZooArchive;
import net.imagej.modelzoo.consumer.ModelZooPredictionOptions;
import net.imagej.modelzoo.consumer.converter.RealIntConverter;
import net.imagej.modelzoo.consumer.model.prediction.ImageInput;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
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
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import java.util.*;

public class ThresholdOptimizer<T extends RealType<T> & NativeType<T>> {

    @Parameter
    private Context context;

    @Parameter
    private DatasetService datasetService;

    @Parameter
    private LogService logService;

    private ModelZooArchive archive;

    private List<Pair<RandomAccessibleInterval<T>, RandomAccessibleInterval<IntType>>> data;

    public ThresholdOptimizer(Context context, ModelZooArchive archive, List<Pair<RandomAccessibleInterval<T>, RandomAccessibleInterval<IntType>>> data){
        context.inject(this);

        this.archive = archive;
        this.data = data;
    }

    public Map<Double, Double> run() throws Exception {
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
        Map<Double, Double> scores = new LinkedHashMap<Double, Double>();
        for (int im=0; im<n; im++) {
            // GT
            final RandomAccessibleInterval<IntType> gt = data.get(im).getB();

            // prediction
            DenoiSegOutput<?, ?> output = singleImagePrediction(prediction, data.get(im).getA());
            final RandomAccessibleInterval<FloatType> seg = (RandomAccessibleInterval<FloatType>) Views.hyperSlice(output.getSegmented(), 2, 1);

            for (int t = 0; t < 9; t++) {
                // threshold foreground prediction to get a mask
                final double threshold = 0.1+0.1*t;
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

                // get score
                double score = getPrecision(gt, labelImg);
                //logService.log(0, "Image "+im+", Threshold "+th+" -> "+score);

                if(scores.containsKey(threshold)){
                    scores.put(threshold, scores.get(threshold)+score/ (double) n);
                } else {
                    scores.put(threshold, score/ (double) n);
                }
            }
        }

        return scores;
    }

    // Code adapted from https://github.com/CellTrackingChallenge
    static protected double getPrecision(RandomAccessibleInterval<IntType> gt, RandomAccessibleInterval<IntType> prediction){
        double precision = 0.;

        // histograms pixels / label
        LinkedHashMap<Integer,Integer> gtHist = new LinkedHashMap<Integer,Integer>();
        LinkedHashMap<Integer,Integer> predHist = new LinkedHashMap<Integer,Integer>();

        Cursor<IntType> c = Views.iterable(gt).localizingCursor();
        RandomAccess<IntType> c2 = prediction.randomAccess();
        while(c.hasNext()){
            // update gt histogram
            Integer label = c.next().getInteger();
            Integer count = gtHist.get(label);
            gtHist.put(label, count == null ? 1 : count+1);

            // update prediction histogram
            c2.setPosition(c);
            label = c2.get().getInteger();
            count = predHist.get(label);
            predHist.put(label, count == null ? 1 : count+1);
        }

        // label pairing matrix
        final int numGtLabels = gtHist.size();
        final int numPredLabels = predHist.size();
        final int[] pairingMatrix = new int[numGtLabels * numPredLabels];
        final ArrayList<Integer> gtLabels = new ArrayList<Integer>(gtHist.keySet());
        final ArrayList<Integer> predLabels = new ArrayList<Integer>(predHist.keySet());

        // calculate intersection
        c.reset();
        while (c.hasNext()) {
            c.next();
            c2.setPosition(c);

            int gtLabel  = c.get().get();
            int predLabel = c2.get().get();

            // if the pixel belongs to an instance in both cases
            if (gtLabel > 0 && predLabel > 0)
                pairingMatrix[gtLabels.indexOf(gtLabel) + numGtLabels * predLabels.indexOf(predLabel)] += 1;
        }

        // for every gt label, find the pred label with maximum overlap
        for (int i=0; i < numGtLabels; i++) {
            int matchingLabel = -1;
            double max_overlap = -1;
            for (int j=0; j < numPredLabels; j++){

                double overlap = (double) pairingMatrix[i + numGtLabels * j];
                if(overlap > max_overlap && overlap > 0){
                    max_overlap = overlap;
                    matchingLabel = j;
                }
                // NB: other approaches calculate % overlap and only select > 0.5
            }

            if (matchingLabel >= 0) {
                double intersection = (double) pairingMatrix[i + numGtLabels * matchingLabel];
                double n_gt = gtHist.get(gtLabels.get(i));
                double n_pred = predHist.get(predLabels.get(matchingLabel));

                precision += intersection / (double) (n_gt + n_pred - intersection);
            }
        }

        return precision / (double) numGtLabels;
    }


    public DenoiSegOutput<?, ?> singleImagePrediction(DenoiSegPrediction prediction, RandomAccessibleInterval<T> image) throws Exception {
        prediction.setInput(new ImageInput("input", image, "XY"));
        prediction.run();

        return prediction.getOutput();
    }

    private ModelZooPredictionOptions createOptions() {
        return ModelZooPredictionOptions.options().numberOfTiles(1).batchSize(10).showProgressDialog(false).convertIntoInputFormat(false);
    }

    // delete from here, only used for the main
    private static <T extends RealType<T>> RandomAccessibleInterval<IntType> convertToInt(RandomAccessibleInterval<T> img) {
        return Converters.convert(img, new RealIntConverter<T>(), new IntType());
    }

    public static void main( final String... args ){
        new ImageJ();

        // Load the image to segment.
        String gtF = "denoiseg_mouse/Y_val/img_5.tif";
        String predF = "denoiseg_mouse/Y_val_pred/pred_5.tif";
        final ImagePlus gtIp = IJ.openImage( gtF);
        final ImagePlus predIp = IJ.openImage( predF);

        final RandomAccessibleInterval<FloatType> img = ImageJFunctions.wrap( gtIp );
        RandomAccessibleInterval<IntType> gt = convertToInt(img);
        final RandomAccessibleInterval<FloatType> img2 = ImageJFunctions.wrap( predIp );
        RandomAccessibleInterval<IntType> pred = convertToInt(img2);

        System.out.println(getPrecision(gt, pred));
    }
}
