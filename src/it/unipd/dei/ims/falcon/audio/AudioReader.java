package it.unipd.dei.ims.falcon.audio;

/**
 * Copyright 2012 University of Padova, Italy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import javax.sound.sampled.*;

/**
 * Read an audio file. Provided that the appropriate packages are in the 
 * classpath, mp3 and ogg should be readable too.
 * The safest route is to read from a MONO, WAV file.
 */
public class AudioReader {

	private AudioInputStream signedBigEndianInputStream = null;
	private AudioInputStream originalAudioInputStream = null;

	public AudioReader(File inputfile) throws UnsupportedAudioFileException, IOException {
		originalAudioInputStream = AudioSystem.getAudioInputStream(inputfile);
		AudioFormat destaf = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
						originalAudioInputStream.getFormat().getSampleRate(),
						16, 1, 16 / 8 * 1, originalAudioInputStream.getFormat().getSampleRate(), true);
		signedBigEndianInputStream = AudioSystem.getAudioInputStream(destaf, originalAudioInputStream);
	}

	/** Read next n samples (at most) from file (interleaved  if channels > 1). Return as double[]. */
	public double[] readDoubleSamples(int n) throws IOException {
		int fl = signedBigEndianInputStream.getFormat().getFrameSize();
		int ss = signedBigEndianInputStream.getFormat().getSampleSizeInBits();
		int ssB = ss / 8;
		//		System.out.println(String.format("fl: %d, ss: %d, ssB: %d, samplerate: %f",
		//						fl, ss, ssB, signedBigEndianInputStream.getFormat().getSampleRate()));
		byte[] b = new byte[n * signedBigEndianInputStream.getFormat().getChannels() * ssB];
		int read = signedBigEndianInputStream.read(b);
		double[] res = new double[read / ssB];
		for (int i = 0; i < res.length; i++) {
			double val = 0;
			switch (ss) {
				case 8:
					val = ((b[i * ssB] & 0xFF) - 128) / 128.0;
					break;
				case 16:
					val = ((b[i * ssB + 0] << 8) | (b[i * ssB + 1] & 0xFF)) / 32768.0;
					break;
				case 24:
					val = ((b[i * ssB + 0] << 16) | ((b[i * ssB + 1] & 0xFF) << 8)
									| (b[i * ssB + 2] & 0xFF)) / 8388606.0;
					break;
				case 32:
					val = ((b[i * ssB + 0] << 24) | ((b[i * ssB + 1] & 0xFF) << 16)
									| ((b[i * ssB + 2] & 0xFF) << 8) | (b[i * ssB + 3] & 0xFF)) / 2147483648.0;
					break;
			}
			res[i] = val;
		}
		return res;
	}

	public float getSampleRate() {
		return signedBigEndianInputStream.getFormat().getSampleRate();

	}

	public void close() throws IOException {
		signedBigEndianInputStream.close();
		originalAudioInputStream.close();
	}
}
