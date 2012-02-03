package it.unipd.dei.ims.falcon.analysis.chromafeatures;

import java.io.IOException;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import it.unipd.dei.ims.falcon.audio.AudioReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ChromaExtraction {

	private final static double A0 = 440. / 16.;

	private static double[] getHammingWindow(int N) {
		double[] win = new double[N];
		for (int n = 0; n < N; n++)
			win[n] = .54 - .46 * Math.cos(2 * Math.PI * n / (N - 1));
		return win;
	}

	private static int closestPowerOfTwo(int n) {
		for (int p = 1; p < 16; p++) {   // ugly code for getting best matching window length
			int h = (int) Math.pow(2, p);
			int l = (int) Math.pow(2, p - 1);
			if (h >= n && l <= n) {
				n = n - l < h - n ? l : h;
				break;
			}
		}
		return n;
	}

	private static int closestPitch(double f) {
		double octs = Math.log10(f / A0) / Math.log10(2);
		octs -= Math.floor(octs);
		octs *= 12;
		return ((int) Math.round(octs)) % 12;
	}

	/**
	 * shift the audio buffer left, and read;
	 * @return true if all n samples were read; false otherise
	 */
	private static boolean shiftAndRead(double[] buffer, AudioReader reader, int n) throws IOException {
		double[] tmp = reader.readDoubleSamples(n);
		if (tmp.length < n)
			return false;
		for (int i = 0; i < buffer.length - n; i++)
			buffer[i] = buffer[i + n];
		for (int i = 0; i < n; i++)
			buffer[buffer.length - n + i] = tmp[i];
		return true;
	}

	private static double[] peakPick(double[] spectrum) {
		double[] pp = new double[spectrum.length];
		for (int i = 1; i < spectrum.length - 1; i++)
			if (spectrum[i] > spectrum[i - 1] && spectrum[i] > spectrum[i + 1])
				pp[i] = spectrum[i];
			else
				pp[i] = 0;
		return pp;
	}

	/**
	 * return a matrix of chroma features, each chroma is a row
	 * @param reader audio stream
	 * @param winLenInMs length in ms; will be rounded to the closes power of two
	 * @param hopsizeRatio 1 = no hopsize, 2 = 50% overlap, 3 = 66% overlap ...
	 * @return 
	 */
	public static List<double[]> getChromaFeatures(AudioReader reader, double winLenInMs, int hopsizeRatio) throws IOException {

		int winLen = closestPowerOfTwo((int) (reader.getSampleRate() * (winLenInMs / 1000.)));
		int hopSize = winLen / hopsizeRatio;

		List<double[]> chromas = new LinkedList<double[]>();
		double[] hammingwin = getHammingWindow(winLen);

		
		double[] audio = new double[winLen];
		shiftAndRead(audio, reader, hopSize * (hopsizeRatio - 1));

		DoubleFFT_1D fftizer = new DoubleFFT_1D(winLen);

		int[] closestPitches = new int[winLen / 2];
		for (int i = 0; i < closestPitches.length; i++) {
			double f = i / reader.getSampleRate();
			closestPitches[i] = closestPitch(f);
		}

		while (true) {
			if (!shiftAndRead(audio, reader, hopSize))
				break;
			double[] fft = Arrays.copyOf(audio, winLen);       // copy buffer 
			for (int i = 0; i < winLen; i++)                    // do windowing
				fft[i] *= hammingwin[i];
			fftizer.realForward(fft);                          // do fft
			double[] spectrum = new double[winLen / 2];          // compute abs fft
			for (int i = 0; i < Math.min(spectrum.length, 10000/ reader.getSampleRate() * winLen); i++)
				spectrum[i] = Math.sqrt(fft[2 * i] * fft[2 * i] + fft[2 * i + 1] * fft[2 * i + 1]);
			spectrum = peakPick(spectrum);                     // spectrum peaks
			double[] chroma = new double[12];
			for(int i = 0; i < chroma.length; i++)
				chroma[i] = 0;
			for (int i = 0; i < closestPitches.length; i++) 
				chroma[closestPitches[i]] += spectrum[i];
			chromas.add(chroma);
		}
		return chromas;
	}
}
