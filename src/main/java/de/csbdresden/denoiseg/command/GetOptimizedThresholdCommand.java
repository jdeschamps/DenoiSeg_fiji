package de.csbdresden.denoiseg.command;

import de.csbdresden.denoiseg.predict.ThresholdOptimizer;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.modelzoo.ModelZooArchive;
import net.imagej.modelzoo.ModelZooService;
import net.imagej.modelzoo.consumer.converter.RealIntConverter;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import org.scijava.Cancelable;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.io.IOService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.scijava.widget.FileWidget.DIRECTORY_STYLE;

@Plugin( type = Command.class, menuPath = "Plugins>CSBDeep>DenoiSeg>Get optimized threshold" )
public class GetOptimizedThresholdCommand implements Command, Cancelable {

    @Parameter(
            label = "Trained model file (.zip)"
    )
    private File modelFile;

    @Parameter(label = "Folder containing validation raw images", style = DIRECTORY_STYLE)
    private File validationRawData;

    @Parameter(label = "Folder containing validation labeling images", style = DIRECTORY_STYLE)
    private File validationLabelingData;

    @Parameter
    private Context context;

    @Parameter
    private IOService ioService;

    @Parameter
    private OpService opService;

    @Parameter
    private ModelZooService modelZooService;

    // TODO tiles parameter for memory efficiency?

    private boolean canceled = false;

    private ThresholdOptimizer thresholdOptimizer;

    private ModelZooArchive archive;

    private List<Pair<RandomAccessibleInterval<FloatType>, RandomAccessibleInterval<IntType>>> data;

    @Override
    public void run() {
        data = new ArrayList<Pair<RandomAccessibleInterval<FloatType>, RandomAccessibleInterval<IntType>>>();

        // load images
        try {
            loadData(validationRawData, validationLabelingData);
        } catch (IOException e) {
            cancel("Failed to load images.");
            e.printStackTrace();
        }

        // load model file
        try {
            getArchive();
        } catch (IOException e) {
            cancel("Failed to load ModelZoo archive.");
            e.printStackTrace();
        }

        // run
        thresholdOptimizer = new ThresholdOptimizer(context, archive, data);
        try {
            thresholdOptimizer.run();
        } catch (Exception e) {
            cancel("Error while running optimizer.");
            e.printStackTrace();
        }

        // get threshold and communicate it back to user
    }

    private void loadData(File validationRawData, File validationLabelingData) throws IOException {
        for (File file : validationRawData.listFiles()) {
            if(canceled) break;
            if(file.isDirectory()) continue;
            Img image = (Img) ioService.open(file.getAbsolutePath());
            RandomAccessibleInterval<IntType> labeling = getLabeling(file, validationLabelingData);
            RandomAccessibleInterval<FloatType> imageFloat = convertToFloat(image);
            data.add(new ValidationPair(imageFloat, labeling));
        }

    }
    private RandomAccessibleInterval<IntType> getLabeling(File rawFile, File labelingDirectory) {
        for (File labeling : labelingDirectory.listFiles()) {
            if(canceled) break;
            if(rawFile.getName().equals(labeling.getName())) {
                try {
                    RandomAccessibleInterval label = (Img) ioService.open(labeling.getAbsolutePath());
                    return convertToInt(label);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private <T extends RealType<T>> RandomAccessibleInterval<FloatType> convertToFloat(RandomAccessibleInterval<T> img) {
        return opService.copy().rai( (RandomAccessibleInterval<FloatType>) Views.iterable(Converters.convert(img, new RealFloatConverter<T>(), new FloatType())));
    }

    private static <T extends RealType<T>> RandomAccessibleInterval<IntType> convertToInt(RandomAccessibleInterval<T> img) {
        return Converters.convert(img, new RealIntConverter<T>(), new IntType());
    }

    private Context getContext() {
        return context;
    }

    protected ModelZooArchive getArchive() throws IOException {
        if (this.archive == null) {
            this.archive = modelZooService.io().open(modelFile);
        }

        return this.archive;
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void cancel(String s) {
        canceled = true;

        // TODO
    }

    @Override
    public String getCancelReason() {
        return "";
    }

    public static void main( final String... args ) throws ExecutionException, InterruptedException {

        final ImageJ imageJ = new ImageJ();
        imageJ.ui().showUI();

        File valX = new File("/Users/deschamp/Downloads/denoiseg_mouse/X_val");
        File valY = new File("/Users/deschamp/Downloads/denoiseg_mouse/Y_val");
        File model = new File("/Users/deschamp/Downloads/denoiseg_mouse/denoiseg-5359585749979253915.bioimage.io.zip");

        imageJ.command().run( GetOptimizedThresholdCommand.class, true,
                "modelFile", model,
                "validationRawData", valX,
                "validationLabelingData", valY).get();
    }

    class ValidationPair implements Pair<RandomAccessibleInterval<FloatType>, RandomAccessibleInterval<IntType>>{

        private RandomAccessibleInterval<FloatType> image;
        private RandomAccessibleInterval<IntType> label;

        public ValidationPair(RandomAccessibleInterval<FloatType> image, RandomAccessibleInterval<IntType> label){
            this.image = image;
            this.label = label;
        }

        @Override
        public RandomAccessibleInterval<FloatType> getA() {
            return image;
        }

        @Override
        public RandomAccessibleInterval<IntType> getB() {
            return label;
        }
    }
}
