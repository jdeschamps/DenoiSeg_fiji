/*-
 * #%L
 * DenoiSeg plugin
 * %%
 * Copyright (C) 2019 - 2020 Center for Systems Biology Dresden
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.csbdresden.denoiseg.predict;

import de.csbdresden.denoiseg.train.DenoiSegModelSpecification;
import de.csbdresden.denoiseg.train.TrainUtils;
import net.imagej.ImageJ;
import net.imagej.modelzoo.ModelZooArchive;
import net.imagej.modelzoo.consumer.DefaultSingleImagePrediction;
import net.imagej.modelzoo.consumer.SanityCheck;
import net.imagej.modelzoo.consumer.SingleImagePrediction;
import net.imagej.ops.OpService;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

@Plugin(type = SingleImagePrediction.class, name = "denoiseg")
public class DenoiSegPrediction <T extends RealType<T>> extends DefaultSingleImagePrediction<T, FloatType> {

	private FloatType mean;
	private FloatType stdDev;

	@Parameter
	private OpService opService;

	@Parameter
	private Context context;

	public DenoiSegPrediction() {
	}

	public DenoiSegPrediction(Context context) {
		super(context);
	}

	@Override
	public void setTrainedModel(ModelZooArchive trainedModel) {
		super.setTrainedModel(trainedModel);
		DenoiSegModelSpecification.setFromSpecification(this, trainedModel.getSpecification());
	}

	public void setMean(FloatType mean) {
		this.mean = mean;
	}

	public void setStdDev(FloatType stdDev) {
		this.stdDev = stdDev;
	}

	@Override
	public <T extends RealType<T>> void setInput(String name, RandomAccessibleInterval<T> value, String axes) {
		super.setInput(name, TrainUtils.normalizeConverter(value, mean, stdDev), axes);
	}

	@Override
	public void run() throws OutOfMemoryError, Exception {
		super.run();
		postprocessOutput(getOutput(), mean, stdDev);

	}

	private void postprocessOutput(RandomAccessibleInterval<FloatType> output, FloatType mean, FloatType stdDev) {
		// only denormalize first channel
		if(output == null) return;
		IntervalView<FloatType> firstChannel = getFirstChannel(output);
		TrainUtils.denormalizeInplace(firstChannel, mean, stdDev, opService);
		IntervalView<FloatType> predictionChannels = getPredictionChannels(output);
		predictionChannels.forEach(pixel -> {
			if(pixel.get() < 0) pixel.set(0);
			if(pixel.get() > 1) pixel.set(1);
		});
	}

	private IntervalView<FloatType> getFirstChannel(RandomAccessibleInterval<FloatType> output) {
		long[] dims = new long[output.numDimensions()];
		output.dimensions(dims);
		dims[dims.length-1] = 1;
		return Views.interval(output, new FinalInterval(dims));
	}

	private IntervalView<FloatType> getPredictionChannels(RandomAccessibleInterval<FloatType> output) {
		long[] minmax = new long[output.numDimensions()*2];
		for (int i = 0; i < output.numDimensions()-1; i++) {
			minmax[i] = 0;
			minmax[i+output.numDimensions()] = output.dimension(i);
		}
		minmax[output.numDimensions()-1] = 1;
		minmax[output.numDimensions()*2-1] = 3;
		return Views.interval(output, Intervals.createMinSize(minmax));
	}

	@Override
	public SanityCheck getSanityCheck() {
		// there is no sanity check implemented for segmentation models yet
		return null;
	}

	public static void main(final String... args ) throws Exception {

		final ImageJ ij = new ImageJ();
		ij.launch( args );
		String modelFile = "/home/random/Development/imagej/project/CSBDeep/training/DenoiSeg/mouse/latest.modelzoo.zip";
		final File predictionInput = new File( "/home/random/Development/imagej/project/CSBDeep/data/DenoiSeg/data/DSB/train_data/10/X_train/img_3.tif" );

		RandomAccessibleInterval _input = ( RandomAccessibleInterval ) ij.io().open( predictionInput.getAbsolutePath() );
		RandomAccessibleInterval _inputConverted = ij.op().copy().rai(ij.op().convert().float32( Views.iterable( _input )));

		DenoiSegPrediction prediction = new DenoiSegPrediction(ij.context());
		prediction.setTrainedModel(modelFile);
		prediction.setNumberOfTiles(1);
		RandomAccessibleInterval output = prediction.predict(_inputConverted, "XY");
		ij.ui().show( output );

	}
}
