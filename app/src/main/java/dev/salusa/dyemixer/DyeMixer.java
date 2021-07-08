// Adapted from http://yehar.com/blog/wp-content/uploads/2006/08/DyeMixer.zip
// This code was released to the public domain by the author on 2020-02-20
// http://yehar.com/blog/?p=307#comment-1256021
package dev.salusa.dyemixer;

import java.applet.Applet;

import java.util.Vector;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.io.Reader;
import java.io.StringReader;
import java.io.InputStream;

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.Window;
import java.awt.color.ColorSpace;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class DyeMixer extends Panel {

    boolean failed = false;

    static interface Spectrum {
        public double get(double wavelen);
        public Spectrum reflectanceToAbsorbance();
        public void clipNegatives();
        public void normalizeAbsorbance();
    }

    static class EvenlySampledSpectrum implements Spectrum {
        float[] samples;
        double start;
        double step;
        public double get(double wavelen) {
            if ((samples == null) || (samples.length == 0)) return 0.0;
            if (samples.length == 1) return samples[0];
            double pos = (wavelen - start)/step;
            if (pos <= 0) return samples[0];
            int intpos = (int)(Math.floor(pos));
            if (intpos >= (samples.length-1)) return samples[samples.length-1];
            double fractpos = pos-intpos;
            return samples[intpos]+(samples[intpos+1]-samples[intpos])*fractpos;
        }
        EvenlySampledSpectrum() {
            samples = null;
        }
        EvenlySampledSpectrum(float[] samples, double start, double step) {
            this.samples = samples;
            this.start = start;
            this.step = step;
        }
        EvenlySampledSpectrum(double[] samples, double start, double step) {
            this.samples = new float[samples.length];
            for (int t = 0; (t < samples.length); t++) {
                this.samples[t] = (float)samples[t];
            }
            this.start = start;
            this.step = step;
        }
        public Spectrum reflectanceToAbsorbance() {
            double scale = -1.0/Math.log(10.0);
            for (int t = 0; (t < samples.length); t++) {
                samples[t] = (float)(Math.log(samples[t])*scale);
            }
            return this;
        }
        public void clipNegatives() {
            for (int t = 0; (t < samples.length); t++) {
                if (samples[t] < 0) samples[t] = 0;
            }
        }
        public void normalizeAbsorbance() {
            if (samples == null) return;
            int normstart = (int)(Math.round(((Light.ciestart-start)/step)));
            int normend = (int)(Math.round(((Light.cieend-start)/step)));
            if (normstart < 0) normstart = 0;
            if (normstart >= samples.length) return;
            if (normend >= samples.length) normend = samples.length-1;
            if (normend < 0) return;
            double max = 0.0;
            for (int t = normstart; (t <= normend); t++) {
                if (samples[t] > max) max = samples[t];
            }
            if (max <= 0.0) return;
            double imax = 1.0/max;
            for (int t = 0; (t < samples.length); t++) {
                samples[t] = (float)(samples[t]*imax);
            }
        }
    }

    static class UnevenlySampledSpectrum implements Spectrum {
        public static class SpectrumSample {
            public float wavelen;
            public float value;
            SpectrumSample(float wavelen, float value) {
                this.wavelen = wavelen;
                this.value = value;
            }
        }
        public Vector samples;
        int poscache; // allows for fast iteration. 0 >= poscache < samples.size() is ensured

        int findInsertPos(float wavelen) {
            if (samples.isEmpty()) return 0;
            float lastwl = ((SpectrumSample)(samples.lastElement())).wavelen;
            if (wavelen >= lastwl) return samples.size();
            float firstwl = ((SpectrumSample)(samples.firstElement())).wavelen;
            if (wavelen <= firstwl) return 0;
            for(;;) {
                if (poscache >= samples.size()-1) poscache = 0;
                float wlthis = ((SpectrumSample)(samples.elementAt(poscache))).wavelen;
                float wlnext = ((SpectrumSample)(samples.elementAt(poscache+1))).wavelen;
                if ((wlthis <= wavelen) && (wlnext >= wavelen)) return poscache+1;
                poscache++;
            }
        }

        public void add(double wavelen, double value) {
            int pos = findInsertPos((float)wavelen);
            if (pos >= samples.size()) samples.addElement(new SpectrumSample((float)wavelen, (float)value));
            else samples.insertElementAt(new SpectrumSample((float)wavelen, (float)value), pos);
        }

        public double get(double wavelen) {
            if (samples.isEmpty()) return 0;
            int pos = findInsertPos((float)wavelen);
            if (pos <= 0) return ((SpectrumSample)(samples.firstElement())).value;
            if (pos >= samples.size()) return ((SpectrumSample)(samples.lastElement())).value;
            SpectrumSample thissample = (SpectrumSample)(samples.elementAt(pos-1));
            SpectrumSample nextsample = (SpectrumSample)(samples.elementAt(pos));
            double wldiff = nextsample.wavelen - thissample.wavelen;
            if (wldiff == 0) return thissample.value;
            double fractpos = (wavelen-thissample.wavelen)/wldiff;
            return thissample.value + fractpos*(nextsample.value-thissample.value);
        }

        UnevenlySampledSpectrum() {
            poscache = 0;
            samples = new Vector();
        }

        UnevenlySampledSpectrum(double[] samples) {
            poscache = 0;
            this.samples = new Vector(samples.length/2);
            for (int t = 0; (t < samples.length/2); t++) {
                add(samples[t*2], samples[t*2+1]);
            }
        }

        public Spectrum reflectanceToAbsorbance() {
            double scale = -1.0/Math.log(10.0);
            for (int t = 0; (t < samples.size()); t++) {
                ((SpectrumSample)(samples.elementAt(t))).value = (float)(Math.log(((SpectrumSample)(samples.elementAt(t))).value)*scale);
            }
            return this;
        }

        public void clipNegatives() {
            for (int t = 0; (t < samples.size()); t++) {
                SpectrumSample sample = (SpectrumSample)(samples.elementAt(t));
                if (sample.value < 0) sample.value = 0;
            }
        }

        public void normalizeAbsorbance() {
            double max = 0.0;
            for (int t = 0; (t < samples.size()); t++) {
                SpectrumSample sample = (SpectrumSample)(samples.elementAt(t));
                if (sample.wavelen >= Light.ciestart) {
                    if (sample.wavelen > Light.cieend) break;
                    if (sample.value > max) max = sample.value;
                }
            }
            if (max <= 0.0) return;
            double imax = 1.0/max;
            for (int t = 0; (t < samples.size()); t++) {
                SpectrumSample sample = (SpectrumSample)(samples.elementAt(t));
                sample.value = (float)(sample.value*imax);
            }
        }

    }

    /** Fills a rectangle with the given color.  */
    static class Swatch extends Component {

        Color color = null;

        /** The CIE x chromaticity of the color, if cieKnown is true.  */
        double ciex;

        /** The CIE y chromaticity of the color, if cieKnown is true.  */
        double ciey;

        /** true if ciex and ciey contain the correct coordinates.  */
        boolean cieknown;

        Dimension preferreddimension = new Dimension(90, 23);

        public void paint(Graphics g) {
            if (color != null) {
                g.setColor(Color.lightGray);
                g.draw3DRect(1, 0, getSize().width - 3, getSize().height - 3, false);
                g.setColor(color);
                g.fillRect(2, 1, getSize().width-4, getSize().height-4);
                if (cieknown) {
                    g.setColor(color.darker());
                    String info = "." + Math.round(ciex*10000) + " ." + Math.round(ciey*10000);
                    int width = g.getFontMetrics().stringWidth(info);
                    int height = g.getFontMetrics().getAscent()-g.getFontMetrics().getDescent();
                    g.drawString(info, (int)((getSize().width-width)/2), (int)((getSize().height+height)/2));
                }
            }
        }

        Swatch() {
            setName("Swatch");
            this.color = null;
            this.cieknown = false;
        }

        Swatch(Color color, int x, int y) {
            setName("Swatch");
            this.color = color;
            this.cieknown = false;
            this.preferreddimension = new Dimension(x, y);
        }

        public void setColor(Color color) {
            this.color = color;
            cieknown = false;
            repaint();
        }

        public void setLight(Light light) {
            color = light.getColor();
            double[] xy = light.getXY();
            ciex = xy[0];
            ciey = xy[1];
            cieknown = true;
            repaint();
        }

        public Dimension getMinimumSize() {
            return new Dimension(64, 23);
        }

        public Dimension getPreferredSize() {
            return preferreddimension;
        }
    }

    static class Light implements Sortable, HasNumber {

        int number;
        public void setNumber(int number) { this.number = number; }
        public int getNumber() { return number; }

        public int compareTo(Sortable s) {
            return StringCompare.compare(this.name, ((Light)s).getName());
        }

        static final int ciestart = 360; // step = 1 nm
        static final int cieend = 830;

        static double getCIEX(int wavelen) {
            if (wavelen < ciestart) return 0.0;
            if (wavelen >= (cie2degx.length+ciestart)) return 0.0;
            return cie2degx[wavelen-ciestart];
        }

        static double getCIEY(int wavelen) {
            if (wavelen < ciestart) return 0.0;
            if (wavelen >= (cie2degy.length+ciestart)) return 0.0;
            return cie2degy[wavelen-ciestart];
        }

        static double getCIEZ(int wavelen) {
            if (wavelen < ciestart) return 0.0;
            if (wavelen >= (cie2degz.length+ciestart)) return 0.0;
            return cie2degz[wavelen-ciestart];
        }

        static final double[] cie2degx = {
                0.0001299, 0.000145847, 0.000163802, 0.000184004, 0.00020669,
                0.0002321, 0.000260728, 0.000293075, 0.000329388, 0.000369914,
                0.0004149, 0.000464159, 0.000518986, 0.000581854, 0.000655235,
                0.0007416, 0.00084503, 0.000964527, 0.001094949, 0.001231154,
                0.001368, 0.00150205, 0.001642328, 0.001802382, 0.001995757,
                0.002236, 0.002535385, 0.002892603, 0.003300829, 0.003753236,
                0.004243, 0.004762389, 0.005330048, 0.005978712, 0.006741117,
                0.00765, 0.008751373, 0.01002888, 0.0114217, 0.01286901,
                0.01431, 0.01570443, 0.01714744, 0.01878122, 0.02074801,
                0.02319, 0.02620736, 0.02978248, 0.03388092, 0.03846824,
                0.04351, 0.0489956, 0.0550226, 0.0617188, 0.069212,
                0.07763, 0.08695811, 0.09717672, 0.1084063, 0.1207672,
                0.13438, 0.1493582, 0.1653957, 0.1819831, 0.198611,
                0.21477, 0.2301868, 0.2448797, 0.2587773, 0.2718079,
                0.2839, 0.2949438, 0.3048965, 0.3137873, 0.3216454,
                0.3285, 0.3343513, 0.3392101, 0.3431213, 0.3461296,
                0.34828, 0.3495999, 0.3501474, 0.350013, 0.349287,
                0.34806, 0.3463733, 0.3442624, 0.3418088, 0.3390941,
                0.3362, 0.3331977, 0.3300411, 0.3266357, 0.3228868,
                0.3187, 0.3140251, 0.308884, 0.3032904, 0.2972579,
                0.2908, 0.2839701, 0.2767214, 0.2689178, 0.2604227,
                0.2511, 0.2408475, 0.2298512, 0.2184072, 0.2068115,
                0.19536, 0.1842136, 0.1733273, 0.1626881, 0.1522833,
                0.1421, 0.1321786, 0.1225696, 0.1132752, 0.1042979,
                0.09564, 0.08729955, 0.07930804, 0.07171776, 0.06458099,
                0.05795001, 0.05186211, 0.04628152, 0.04115088, 0.03641283,
                0.03201, 0.0279172, 0.0241444, 0.020687, 0.0175404,
                0.0147, 0.01216179, 0.00991996, 0.00796724, 0.006296346,
                0.0049, 0.003777173, 0.00294532, 0.00242488, 0.002236293,
                0.0024, 0.00292552, 0.00383656, 0.00517484, 0.00698208,
                0.0093, 0.01214949, 0.01553588, 0.01947752, 0.02399277,
                0.0291, 0.03481485, 0.04112016, 0.04798504, 0.05537861,
                0.06327, 0.07163501, 0.08046224, 0.08973996, 0.09945645,
                0.1096, 0.1201674, 0.1311145, 0.1423679, 0.1538542,
                0.1655, 0.1772571, 0.18914, 0.2011694, 0.2133658,
                0.2257499, 0.2383209, 0.2510668, 0.2639922, 0.2771017,
                0.2904, 0.3038912, 0.3175726, 0.3314384, 0.3454828,
                0.3597, 0.3740839, 0.3886396, 0.4033784, 0.4183115,
                0.4334499, 0.4487953, 0.464336, 0.480064, 0.4959713,
                0.5120501, 0.5282959, 0.5446916, 0.5612094, 0.5778215,
                0.5945, 0.6112209, 0.6279758, 0.6447602, 0.6615697,
                0.6784, 0.6952392, 0.7120586, 0.7288284, 0.7455188,
                0.7621, 0.7785432, 0.7948256, 0.8109264, 0.8268248,
                0.8425, 0.8579325, 0.8730816, 0.8878944, 0.9023181,
                0.9163, 0.9297995, 0.9427984, 0.9552776, 0.9672179,
                0.9786, 0.9893856, 0.9995488, 1.0090892, 1.0180064,
                1.0263, 1.0339827, 1.040986, 1.047188, 1.0524667,
                1.0567, 1.0597944, 1.0617992, 1.0628068, 1.0629096,
                1.0622, 1.0607352, 1.0584436, 1.0552244, 1.0509768,
                1.0456, 1.0390369, 1.0313608, 1.0226662, 1.0130477,
                1.0026, 0.9913675, 0.9793314, 0.9664916, 0.9528479,
                0.9384, 0.923194, 0.907244, 0.890502, 0.87292,
                0.8544499, 0.835084, 0.814946, 0.794186, 0.772954,
                0.7514, 0.7295836, 0.7075888, 0.6856022, 0.6638104,
                0.6424, 0.6215149, 0.6011138, 0.5811052, 0.5613977,
                0.5419, 0.5225995, 0.5035464, 0.4847436, 0.4661939,
                0.4479, 0.4298613, 0.412098, 0.394644, 0.3775333,
                0.3608, 0.3444563, 0.3285168, 0.3130192, 0.2980011,
                0.2835, 0.2695448, 0.2561184, 0.2431896, 0.2307272,
                0.2187, 0.2070971, 0.1959232, 0.1851708, 0.1748323,
                0.1649, 0.1553667, 0.14623, 0.13749, 0.1291467,
                0.1212, 0.1136397, 0.106465, 0.09969044, 0.09333061,
                0.0874, 0.08190096, 0.07680428, 0.07207712, 0.06768664,
                0.0636, 0.05980685, 0.05628216, 0.05297104, 0.04981861,
                0.04677, 0.04378405, 0.04087536, 0.03807264, 0.03540461,
                0.0329, 0.03056419, 0.02838056, 0.02634484, 0.02445275,
                0.0227, 0.02108429, 0.01959988, 0.01823732, 0.01698717,
                0.01584, 0.01479064, 0.01383132, 0.01294868, 0.0121292,
                0.01135916, 0.01062935, 0.009938846, 0.009288422, 0.008678854,
                0.008110916, 0.007582388, 0.007088746, 0.006627313, 0.006195408,
                0.005790346, 0.005409826, 0.005052583, 0.004717512, 0.004403507,
                0.004109457, 0.003833913, 0.003575748, 0.003334342, 0.003109075,
                0.002899327, 0.002704348, 0.00252302, 0.002354168, 0.002196616,
                0.00204919, 0.00191096, 0.001781438, 0.00166011, 0.001546459,
                0.001439971, 0.001340042, 0.001246275, 0.001158471, 0.00107643,
                0.000999949, 0.000928736, 0.000862433, 0.00080075, 0.000743396,
                0.000690079, 0.000640516, 0.000594502, 0.000551865, 0.000512429,
                0.000476021, 0.000442454, 0.000411512, 0.000382981, 0.000356649,
                0.000332301, 0.000309759, 0.000288887, 0.000269539, 0.000251568,
                0.000234826, 0.000219171, 0.000204526, 0.000190841, 0.000178065,
                0.000166151, 0.000155024, 0.000144622, 0.00013491, 0.000125852,
                0.000117413, 0.000109552, 0.000102225, 9.53945E-05, 8.90239E-05,
                8.30753E-05, 7.75127E-05, 7.2313E-05, 6.74578E-05, 6.29284E-05,
                5.87065E-05, 5.47703E-05, 5.10992E-05, 4.76765E-05, 4.44857E-05,
                4.15099E-05, 3.87332E-05, 3.6142E-05, 3.37235E-05, 3.14649E-05,
                2.93533E-05, 2.73757E-05, 2.55243E-05, 2.37938E-05, 2.21787E-05,
                2.06738E-05, 1.92723E-05, 1.79664E-05, 1.67499E-05, 1.56165E-05,
                1.45598E-05, 1.35739E-05, 1.26544E-05, 1.17972E-05, 1.09984E-05,
                1.0254E-05, 9.55965E-06, 8.91204E-06, 8.30836E-06, 7.74577E-06,
                7.22146E-06, 6.73248E-06, 6.27642E-06, 5.8513E-06, 5.45512E-06,
                5.08587E-06, 4.74147E-06, 4.42024E-06, 4.12078E-06, 3.84172E-06,
                3.58165E-06, 3.33913E-06, 3.11295E-06, 2.90212E-06, 2.70565E-06,
                2.52253E-06, 2.35173E-06, 2.19242E-06, 2.0439E-06, 1.9055E-06,
                1.77651E-06, 1.65622E-06, 1.54402E-06, 1.43944E-06, 1.34198E-06,
                1.25114E-06
        };

        static final double[] cie2degy = {
                0.000003917, 4.39358E-06, 4.9296E-06, 5.53214E-06, 6.20825E-06,
                0.000006965, 7.81322E-06, 8.76734E-06, 9.83984E-06, 1.10432E-05,
                0.00001239, 1.38864E-05, 1.55573E-05, 1.7443E-05, 1.95838E-05,
                0.00002202, 2.48397E-05, 2.80413E-05, 3.1531E-05, 3.52152E-05,
                0.000039, 4.28264E-05, 4.69146E-05, 5.15896E-05, 5.71764E-05,
                0.000064, 7.23442E-05, 8.22122E-05, 9.35082E-05, 0.000106136,
                0.00012, 0.000134984, 0.000151492, 0.000170208, 0.000191816,
                0.000217, 0.000246907, 0.00028124, 0.00031852, 0.000357267,
                0.000396, 0.000433715, 0.000473024, 0.000517876, 0.000572219,
                0.00064, 0.00072456, 0.0008255, 0.00094116, 0.00106988,
                0.00121, 0.001362091, 0.001530752, 0.001720368, 0.001935323,
                0.00218, 0.0024548, 0.002764, 0.0031178, 0.0035264,
                0.004, 0.00454624, 0.00515932, 0.00582928, 0.00654616,
                0.0073, 0.008086507, 0.00890872, 0.00976768, 0.01066443,
                0.0116, 0.01257317, 0.01358272, 0.01462968, 0.01571509,
                0.01684, 0.01800736, 0.01921448, 0.02045392, 0.02171824,
                0.023, 0.02429461, 0.02561024, 0.02695857, 0.02835125,
                0.0298, 0.03131083, 0.03288368, 0.03452112, 0.03622571,
                0.038, 0.03984667, 0.041768, 0.043766, 0.04584267,
                0.048, 0.05024368, 0.05257304, 0.05498056, 0.05745872,
                0.06, 0.06260197, 0.06527752, 0.06804208, 0.07091109,
                0.0739, 0.077016, 0.0802664, 0.0836668, 0.0872328,
                0.09098, 0.09491755, 0.09904584, 0.1033674, 0.1078846,
                0.1126, 0.117532, 0.1226744, 0.1279928, 0.1334528,
                0.13902, 0.1446764, 0.1504693, 0.1564619, 0.1627177,
                0.1693, 0.1762431, 0.1835581, 0.1912735, 0.199418,
                0.20802, 0.2171199, 0.2267345, 0.2368571, 0.2474812,
                0.2586, 0.2701849, 0.2822939, 0.2950505, 0.308578,
                0.323, 0.3384021, 0.3546858, 0.3716986, 0.3892875,
                0.4073, 0.4256299, 0.4443096, 0.4633944, 0.4829395,
                0.503, 0.5235693, 0.544512, 0.56569, 0.5869653,
                0.6082, 0.6293456, 0.6503068, 0.6708752, 0.6908424,
                0.71, 0.7281852, 0.7454636, 0.7619694, 0.7778368,
                0.7932, 0.8081104, 0.8224962, 0.8363068, 0.8494916,
                0.862, 0.8738108, 0.8849624, 0.8954936, 0.9054432,
                0.9148501, 0.9237348, 0.9320924, 0.9399226, 0.9472252,
                0.954, 0.9602561, 0.9660074, 0.9712606, 0.9760225,
                0.9803, 0.9840924, 0.9874182, 0.9903128, 0.9928116,
                0.9949501, 0.9967108, 0.9980983, 0.999112, 0.9997482,
                1, 0.9998567, 0.9993046, 0.9983255, 0.9968987,
                0.995, 0.9926005, 0.9897426, 0.9864444, 0.9827241,
                0.9786, 0.9740837, 0.9691712, 0.9638568, 0.9581349,
                0.952, 0.9454504, 0.9384992, 0.9311628, 0.9234576,
                0.9154, 0.9070064, 0.8982772, 0.8892048, 0.8797816,
                0.87, 0.8598613, 0.849392, 0.838622, 0.8275813,
                0.8163, 0.8047947, 0.793082, 0.781192, 0.7691547,
                0.757, 0.7447541, 0.7324224, 0.7200036, 0.7074965,
                0.6949, 0.6822192, 0.6694716, 0.6566744, 0.6438448,
                0.631, 0.6181555, 0.6053144, 0.5924756, 0.5796379,
                0.5668, 0.5539611, 0.5411372, 0.5283528, 0.5156323,
                0.503, 0.4904688, 0.4780304, 0.4656776, 0.4534032,
                0.4412, 0.42908, 0.417036, 0.405032, 0.393032,
                0.381, 0.3689184, 0.3568272, 0.3447768, 0.3328176,
                0.321, 0.3093381, 0.2978504, 0.2865936, 0.2756245,
                0.265, 0.2547632, 0.2448896, 0.2353344, 0.2260528,
                0.217, 0.2081616, 0.1995488, 0.1911552, 0.1829744,
                0.175, 0.1672235, 0.1596464, 0.1522776, 0.1451259,
                0.1382, 0.1315003, 0.1250248, 0.1187792, 0.1127691,
                0.107, 0.1014762, 0.09618864, 0.09112296, 0.08626485,
                0.0816, 0.07712064, 0.07282552, 0.06871008, 0.06476976,
                0.061, 0.05739621, 0.05395504, 0.05067376, 0.04754965,
                0.04458, 0.04175872, 0.03908496, 0.03656384, 0.03420048,
                0.032, 0.02996261, 0.02807664, 0.02632936, 0.02470805,
                0.0232, 0.02180077, 0.02050112, 0.01928108, 0.01812069,
                0.017, 0.01590379, 0.01483718, 0.01381068, 0.01283478,
                0.01192, 0.01106831, 0.01027339, 0.009533311, 0.008846157,
                0.00821, 0.007623781, 0.007085424, 0.006591476, 0.006138485,
                0.005723, 0.005343059, 0.004995796, 0.004676404, 0.004380075,
                0.004102, 0.003838453, 0.003589099, 0.003354219, 0.003134093,
                0.002929, 0.002738139, 0.002559876, 0.002393244, 0.002237275,
                0.002091, 0.001953587, 0.00182458, 0.00170358, 0.001590187,
                0.001484, 0.001384496, 0.001291268, 0.001204092, 0.001122744,
                0.001047, 0.00097659, 0.000911109, 0.000850133, 0.000793238,
                0.00074, 0.000690083, 0.00064331, 0.000599496, 0.000558455,
                0.00052, 0.000483914, 0.000450053, 0.000418345, 0.000388718,
                0.0003611, 0.000335384, 0.00031144, 0.000289166, 0.000268454,
                0.0002492, 0.000231302, 0.000214686, 0.000199288, 0.000185048,
                0.0001719, 0.000159778, 0.000148604, 0.000138302, 0.000128793,
                0.00012, 0.00011186, 0.000104322, 9.73356E-05, 9.08459E-05,
                0.0000848, 7.91467E-05, 0.000073858, 0.000068916, 6.43027E-05,
                0.00006, 5.59819E-05, 5.22256E-05, 4.87184E-05, 4.54475E-05,
                0.0000424, 3.9561E-05, 3.69151E-05, 3.44487E-05, 3.21482E-05,
                0.00003, 2.79913E-05, 2.61136E-05, 2.43602E-05, 2.27246E-05,
                0.0000212, 1.97786E-05, 1.84529E-05, 1.72169E-05, 1.60646E-05,
                0.00001499, 1.39873E-05, 1.30516E-05, 1.21782E-05, 1.13625E-05,
                0.0000106, 9.88588E-06, 9.2173E-06, 8.59236E-06, 8.00913E-06,
                7.4657E-06, 6.95957E-06, 6.488E-06, 6.0487E-06, 5.6394E-06,
                5.2578E-06, 4.90177E-06, 4.56972E-06, 4.26019E-06, 3.97174E-06,
                3.7029E-06, 3.45216E-06, 3.2183E-06, 3.0003E-06, 2.79714E-06,
                2.6078E-06, 2.43122E-06, 2.26653E-06, 2.11301E-06, 1.96994E-06,
                1.8366E-06, 1.71223E-06, 1.59623E-06, 1.48809E-06, 1.38731E-06,
                1.2934E-06, 1.20582E-06, 1.12414E-06, 1.04801E-06, 9.77058E-07,
                9.1093E-07, 8.49251E-07, 7.91721E-07, 7.3809E-07, 6.8811E-07,
                6.4153E-07, 5.9809E-07, 5.57575E-07, 5.19808E-07, 4.84612E-07,
                4.5181E-07
        };

        static final double[] cie2degz = {
                0.0006061, 0.000680879, 0.000765146, 0.000860012, 0.000966593,
                0.001086, 0.001220586, 0.001372729, 0.001543579, 0.001734286,
                0.001946, 0.002177777, 0.002435809, 0.002731953, 0.003078064,
                0.003486, 0.003975227, 0.00454088, 0.00515832, 0.005802907,
                0.006450001, 0.007083216, 0.007745488, 0.008501152, 0.009414544,
                0.01054999, 0.0119658, 0.01365587, 0.01558805, 0.01773015,
                0.02005001, 0.02251136, 0.02520288, 0.02827972, 0.03189704,
                0.03621, 0.04143771, 0.04750372, 0.05411988, 0.06099803,
                0.06785001, 0.07448632, 0.08136156, 0.08915364, 0.09854048,
                0.1102, 0.1246133, 0.1417017, 0.1613035, 0.1832568,
                0.2074, 0.2336921, 0.2626114, 0.2947746, 0.3307985,
                0.3713, 0.4162091, 0.4654642, 0.5196948, 0.5795303,
                0.6456, 0.7184838, 0.7967133, 0.8778459, 0.959439,
                1.0390501, 1.1153673, 1.1884971, 1.2581233, 1.3239296,
                1.3856, 1.4426352, 1.4948035, 1.5421903, 1.5848807,
                1.62296, 1.6564048, 1.6852959, 1.7098745, 1.7303821,
                1.74706, 1.7600446, 1.7696233, 1.7762637, 1.7804334,
                1.7826, 1.7829682, 1.7816998, 1.7791982, 1.7758671,
                1.77211, 1.7682589, 1.764039, 1.7589438, 1.7524663,
                1.7441, 1.7335595, 1.7208581, 1.7059369, 1.6887372,
                1.6692, 1.6475287, 1.6234127, 1.5960223, 1.564528,
                1.5281, 1.4861114, 1.4395215, 1.3898799, 1.3387362,
                1.28764, 1.2374223, 1.1878243, 1.1387611, 1.090148,
                1.0419, 0.9941976, 0.9473473, 0.9014531, 0.8566193,
                0.8129501, 0.7705173, 0.7294448, 0.6899136, 0.6521049,
                0.6162, 0.5823286, 0.5504162, 0.5203376, 0.4919673,
                0.46518, 0.4399246, 0.4161836, 0.3938822, 0.3729459,
                0.3533, 0.3348578, 0.3175521, 0.3013375, 0.2861686,
                0.272, 0.2588171, 0.2464838, 0.2347718, 0.2234533,
                0.2123, 0.2011692, 0.1901196, 0.1792254, 0.1685608,
                0.1582, 0.1481383, 0.1383758, 0.1289942, 0.1200751,
                0.1117, 0.1039048, 0.09666748, 0.08998272, 0.08384531,
                0.07824999, 0.07320899, 0.06867816, 0.06456784, 0.06078835,
                0.05725001, 0.05390435, 0.05074664, 0.04775276, 0.04489859,
                0.04216, 0.03950728, 0.03693564, 0.03445836, 0.03208872,
                0.02984, 0.02771181, 0.02569444, 0.02378716, 0.02198925,
                0.0203, 0.01871805, 0.01724036, 0.01586364, 0.01458461,
                0.0134, 0.01230723, 0.01130188, 0.01037792, 0.009529306,
                0.008749999, 0.0080352, 0.0073816, 0.0067854, 0.0062428,
                0.005749999, 0.0053036, 0.0048998, 0.0045342, 0.0042024,
                0.0039, 0.0036232, 0.0033706, 0.0031414, 0.0029348,
                0.002749999, 0.0025852, 0.0024386, 0.0023094, 0.0021968,
                0.0021, 0.002017733, 0.0019482, 0.0018898, 0.001840933,
                0.0018, 0.001766267, 0.0017378, 0.0017112, 0.001683067,
                0.001650001, 0.001610133, 0.0015644, 0.0015136, 0.001458533,
                0.0014, 0.001336667, 0.00127, 0.001205, 0.001146667,
                0.0011, 0.0010688, 0.0010494, 0.0010356, 0.0010212,
                0.001, 0.00096864, 0.00092992, 0.00088688, 0.00084256,
                0.0008, 0.00076096, 0.00072368, 0.00068592, 0.00064544,
                0.0006, 0.000547867, 0.0004916, 0.0004354, 0.000383467,
                0.00034, 0.000307253, 0.00028316, 0.00026544, 0.000251813,
                0.00024, 0.000229547, 0.00022064, 0.00021196, 0.000202187,
                0.00019, 0.000174213, 0.00015564, 0.00013596, 0.000116853,
                0.0001, 8.61333E-05, 0.0000746, 0.000065, 5.69333E-05,
                5E-05, 0.00004416, 0.00003948, 0.00003572, 0.00003264,
                0.00003, 2.76533E-05, 0.00002556, 0.00002364, 2.18133E-05,
                0.00002, 1.81333E-05, 0.0000162, 0.0000142, 1.21333E-05,
                0.00001, 7.73333E-06, 0.0000054, 0.0000032, 1.33333E-06
        };

        static Light whitewl = new WhiteLightWL();
        static Light whitef = new WhiteLightF();

        //reference="ISO/CIE 10526-1991, Colorimetric illuminants";
        // ISO/CIE data is at 1 nm steps, but is linearly interpolated from 10 nm step size,
        // which is also adopted in this data. The 5 nm step size data from CIE 15.2-1986 is
        // also linearly interpolated from 10 nm step size.
        static Light d65() {
            Light newlight = new Light("daylight, standard, CIE D 65", new EvenlySampledSpectrum(new double[] {
                    0.0341, 3.2945, 20.236, 37.0535, 39.9488, 44.9117, 46.6383, 52.0891,
                    49.9755, 54.6482,  82.7549, 91.486, 93.4318, 86.6823, 104.865,
                    117.008, 117.812, 114.861, 115.923, 108.811,  109.354, 107.802,
                    104.79, 107.689, 104.405, 104.046, 100, 96.3342, 95.788, 88.6856,
                    90.0062, 89.5991, 87.6987, 83.2886, 83.6992, 80.0268, 80.2146,
                    82.2778, 78.2842, 69.7213,  71.6091, 74.349, 61.604, 69.8856, 75.087,
                    63.5927, 46.4182, 66.8054, 63.3828, 64.304,  59.4519, 51.959, 57.4406,
                    60.3125}, 300, 10));
            newlight.setNumber(1);
            return newlight;
        }

        static class WhiteLightWL extends Light {
            WhiteLightWL() {
                powerspectrum = new double[cieend-ciestart+1];
                for(int wl = ciestart; (wl <= cieend); wl++) {
                    setPowerSpectrum(wl, 1);
                }
                setName("reference, flat over wavelen");
                normalize();
            }
        }

        static class WhiteLightF extends Light {
            WhiteLightF() {
                powerspectrum = new double[cieend-ciestart+1];
                double peakpower = 0;
                for(int wl = ciestart; (wl <= cieend); wl++) {
                    double power = 1.0/((double)wl*(double)wl);
                    if (power > peakpower) peakpower = power;
                    setPowerSpectrum(wl, power);
                }
                double invpeakpower = 1.0/peakpower;
                for(int wl = ciestart; (wl <= cieend); wl++) {
                    setPowerSpectrum(wl, getPowerSpectrum(wl)*invpeakpower);
                }
                setName("reference, flat over frequency");
                normalize();
            }
        }

        Light() {
            setNumber(0);
        }

        double[] powerspectrum;
        String name;
        double xscale = 1.0, yscale = 1.0, zscale = 1.0;

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }

        // Return filtered copy of this light
        Light getFiltered(Absorber absorber, double strength) {
            double[] newpowerspectrum = new double[cieend-ciestart+1];
            Light newlight = new Light(name, newpowerspectrum);
            if (absorber == null) return getCopy();
            for(int wl = ciestart; (wl <= cieend); wl++) {
                newlight.setPowerSpectrum(wl, getPowerSpectrum(wl)*
                        Math.pow(10.0, -absorber.getAbsorbance(wl)*strength));
            }
            newlight.xscale = xscale;
            newlight.yscale = yscale;
            newlight.zscale = zscale;
            return newlight;
        }

        // Filter this light
        void filter(Absorber absorber, double strength) {
            if (absorber == null) return;
            for(int wl = ciestart; (wl <= cieend); wl++) {
                setPowerSpectrum(wl, getPowerSpectrum(wl)*
                        Math.pow(10.0, -absorber.getAbsorbance(wl)*strength));
            }
        }

        Light getCopy() {
            double[] newpowerspectrum = new double[cieend-ciestart+1];
            Light newlight = new Light(name, newpowerspectrum);
            for(int wl = ciestart; (wl <= cieend); wl++) {
                newlight.setPowerSpectrum(wl, getPowerSpectrum(wl));
            }
            newlight.xscale = xscale;
            newlight.yscale = yscale;
            newlight.zscale = zscale;
            return newlight;
        }

        Spectrum getPowerSpectrum() {
            return new EvenlySampledSpectrum(powerspectrum, ciestart, 1);
        }

        double getPowerSpectrum(int wavelen) {
            if (wavelen < ciestart) return 0;
            if (wavelen-ciestart >= powerspectrum.length) return 0;
            return powerspectrum[wavelen-ciestart];
        }

        void setPowerSpectrum(int wavelen, double power) {
            if (wavelen < ciestart) return;
            if (wavelen-ciestart >= powerspectrum.length) return;
            powerspectrum[wavelen-ciestart] = power;
        }

        double[] getXY() {
            double x = 0, y = 0, z = 0;
            for(int wl = ciestart; (wl <= cieend); wl++) {
                x += getCIEX(wl)*getPowerSpectrum(wl);
                y += getCIEY(wl)*getPowerSpectrum(wl);
                z += getCIEZ(wl)*getPowerSpectrum(wl);
            }
            double invall = 1.0/(x+y+z);
            return new double[] {x*invall, y*invall};
        }

        double getX() {
            double x = 0;
            for (int wl = ciestart; (wl <= cieend); wl++) {
                x += getCIEX(wl)*getPowerSpectrum(wl);
            }
            return x;
        }

        double getY() {
            double y = 0;
            for (int wl = ciestart; (wl <= cieend); wl++) {
                y += getCIEY(wl)*getPowerSpectrum(wl);
            }
            return y;
        }

        double getZ() {
            double z = 0;
            for (int wl = ciestart; (wl <= cieend); wl++) {
                z += getCIEZ(wl)*getPowerSpectrum(wl);
            }
            return z;
        }

        double[] getXYZ() {
            double[] xyz = new double[3];
            xyz[0] = 0;
            xyz[1] = 0;
            xyz[2] = 0;
            for(int wl = ciestart; (wl <= cieend); wl++) {
                xyz[0] += getCIEX(wl)*getPowerSpectrum(wl);
                xyz[1] += getCIEY(wl)*getPowerSpectrum(wl);
                xyz[2] += getCIEZ(wl)*getPowerSpectrum(wl);
            }
            return xyz;
        }

        double[] xyz2sRGBUncut(double[] xyz) {
            double[] rgb = new double[3];
            rgb[0] = 3.2410*xyz[0] - 1.5374*xyz[1] - 0.4986*xyz[2];
            rgb[1] = -0.9692*xyz[0] + 1.8760*xyz[1] + 0.0416*xyz[2];
            rgb[2] = 0.0556*xyz[0] - 0.2040*xyz[1] + 1.0570*xyz[2];
            return rgb;
        }

        int[] sRGBCut(double[] rgb) {
            int[] cutrgb = new int[3];
            for (int t = 0; (t < 3); t++) {
                if (rgb[t] <= 0.00304) rgb[t] *= 12.92;
                else rgb[t] = 1.055 * Math.pow(rgb[t], 1.0/2.4) - 0.055;
                if (rgb[t] < 0) cutrgb[t] = 0;
                else if (rgb[t] > 1) cutrgb[t] = 255;
                else cutrgb[t] = Math.round((float)(rgb[t]*255.0));
            }
            return cutrgb;
        }

        int[] getsRGB() {
            double[] xyz = getXYZ();
            xyz[0] *= xscale;
            xyz[1] *= yscale;
            xyz[2] *= zscale;
            return sRGBCut(xyz2sRGBUncut(xyz));
        }

        void normalize() {
            double[] xyz = getXYZ();
            xyz[0] *= xscale;
            xyz[1] *= yscale;
            xyz[2] *= zscale;
            double[] rgb = xyz2sRGBUncut(xyz);
            double max = rgb[0];
            if (rgb[1] > max) max = rgb[1];
            if (rgb[2] > max) max = rgb[2];
            if (max > 0) {
                double scale = 1.0/max;
                xscale *= scale;
                yscale *= scale;
                zscale *= scale;
            }
        }

        void normalizeWhite() {
            double[] xyz = getXYZ();
            // fullscale white is encoded as xyz={0.9505, 1, 1.0890)
            if (xyz[0] > 0) xscale = 0.9505/xyz[0]; else xscale = 1.0;
            if (xyz[1] > 0) yscale = 1.0000/xyz[1]; else yscale = 1.0;
            if (xyz[2] > 0) zscale = 1.0890/xyz[2]; else zscale = 1.0;
        }

        Color getColor() {
            int[] rgb = getsRGB();
            return new Color(rgb[0], rgb[1], rgb[2]);
        }

        Light(String name, Spectrum powerspectrum) {
            setName(name);
            this.powerspectrum = new double[cieend-ciestart+1];
            for (int t = ciestart; (t <= cieend); t++) {
                this.powerspectrum[t-ciestart] = powerspectrum.get(t);
            }
            normalize();
            setNumber(0);
        }

        Light(String name, double[] powerspectrum) {
            setName(name);
            this.powerspectrum = powerspectrum;
            normalize();
            setNumber(0);
        }

        // Black body radiation at temperature t Kelvins
        // double w = (2.0*c*c*h*Pi)/(pow(a,5.0)*(-1.0 + exp((c*h)/(a*k*t))))
        // w = energy j/m^2 s
        // c = speed of light, 299792458 m/s
        // a = wavelen meters
        // t = temperature Kelvins
        // k = Boltzmann's Constant, 1.3806503e-23 J K^-1
        // h = Planck's Constant, 6.62606876e-34 J s
        // Pi = I think you know this one:
        Light(double t) {
            powerspectrum = new double[cieend-ciestart+1];
            double peakpower = 0;
            for(int wl = ciestart; (wl <= cieend); wl++) {
                double x = (double)wl;
                double power = 1.0/((x*x)*(x*x)*x*(Math.exp(14387752.21/(x*t))-1.0));
                if (power > peakpower) peakpower = power;
                setPowerSpectrum(wl, power);
            }
            double invpeakpower = 1.0/peakpower;
            for(int wl = ciestart; (wl <= cieend); wl++) {
                setPowerSpectrum(wl, getPowerSpectrum(wl)*invpeakpower);
            }
            setName("reference, blackbody, "+ (int)t +" K");
            normalize();
            setNumber(0);
        }
    }

    private static interface Absorber {
        // All wavelengths are in nm.
        // absorbance = -log10(out/in)
        public double getAbsorbance(double wavelen);
    }

    private static class Canvas implements Absorber, Sortable, HasNumber {
        int number;
        public void setNumber(int number) { this.number = number; }
        public int getNumber() { return number; }

        public int compareTo(Sortable s) {
            return StringCompare.compare(this.name, ((Canvas)s).getName());
        }

        Spectrum absorbancespectrum;
        public double getAbsorbance(double wavelen) {
            if (absorbancespectrum == null) return 0;
            return absorbancespectrum.get(wavelen);
        }

        private String name;
        public String getName() {
            return name;
        }
        public Canvas() {
            name = "blank (perfect white)";
            absorbancespectrum = new EvenlySampledSpectrum(new double[] {0.0}, 1, 1);
            setNumber(1);
        }

        Canvas(String name, Spectrum reflectancespectrum) {
            absorbancespectrum = reflectancespectrum.reflectanceToAbsorbance();
            this.name = name;
            setNumber(0);
        }
    }

    static class Dye implements Absorber, Sortable, HasNumber {

        int number;
        public void setNumber(int number) { this.number = number; }
        public int getNumber() { return number; }

        public int compareTo(Sortable s) {
            return StringCompare.compare(this.name, ((Dye)s).getName());
        }

        Spectrum absorbancespectrum;
        public double getAbsorbance(double wavelen) {
            if (absorbancespectrum == null) return 0;
            return absorbancespectrum.get(wavelen);
        }

        String name;
        String ci;
        String family;
        String mixture;

        public String getName() {
            return name;
        }

        Dye(String name, String ci, String family, String mixture, Spectrum spectrum) {
            this.name = name;
            this.ci = ci;
            this.family = family;
            this.mixture = mixture;
            this.absorbancespectrum = spectrum;
            setNumber(0);
        }

        Dye(String name, String ci, String family, String mixture, Spectrum spectrum, int number) {
            this.name = name;
            this.ci = ci;
            this.family = family;
            this.mixture = mixture;
            this.absorbancespectrum = spectrum;
            setNumber(number);
        }

        static final Dye blank =
                new Dye("blank", "n/a",
                        "n/a",
                        "n/a",
                        new EvenlySampledSpectrum(new double[] {0.0}, 1, 1),
                        1);

        static final Dye black =
                new Dye("reference, black", "n/a",
                        "n/a",
                        "n/a",
                        new EvenlySampledSpectrum(new double[] {1.0}, 1, 1),
                        2);
    }

    static final int nummixeddyes = 8;

    Vector availablecanvases;
    Canvas[] generatedcanvases = {
            new Canvas(),
    };

    Vector availabledyes;
    Dye[] generateddyes = {
            Dye.blank,
            Dye.black
    };

    Vector availablelights;
    Light[] generatedlights = {
            Light.d65(),
            new Light(800),
            new Light(900),
            new Light(1000),
            new Light(1100),
            new Light(1200),
            new Light(1300),
            new Light(1400),
            new Light(1500),
            new Light(1600),
            new Light(1700),
            new Light(1800),
            new Light(1900),
            new Light(2000),
            new Light(2100),
            new Light(2200),
            new Light(2300),
            new Light(2400),
            new Light(2500),
            new Light(2600),
            new Light(2700),
            new Light(2800),
            new Light(2900),
            new Light(3000),
            new Light(3100),
            new Light(3200),
            new Light(3300),
            new Light(3400),
            new Light(3500),
            new Light(3600),
            new Light(3700),
            new Light(3800),
            new Light(3900),
            new Light(4000),
            new Light(4100),
            new Light(4200),
            new Light(4300),
            new Light(4400),
            new Light(4500),
            new Light(4600),
            new Light(4700),
            new Light(4800),
            new Light(4900),
            new Light(5000),
            new Light(5100),
            new Light(5200),
            new Light(5300),
            new Light(5400),
            new Light(5500),
            new Light(5750),
            new Light(6000),
            new Light(6250),
            new Light(6500),
            new Light(6750),
            new Light(7000),
            new Light(7250),
            new Light(7500),
            new Light(7750),
            new Light(8000),
            new Light(8250),
            new Light(8500),
            new Light(8750),
            new Light(9000),
            new Light(9250),
            new Light(9500),
            new Light(9750),
            new Light(10000),
            new Light(10500),
            new Light(11000),
            new Light(11500),
            new Light(12000),
            new Light(12500),
            new Light(13000),
            new Light(14000),
            new Light(15000),
            new Light(17500),
            new Light(20000),
            new Light(25000),
            new Light(30000),
            new Light(35000),
            new Light(40000),
            new Light(60000),
            new Light(100000),
            Light.whitef,
            Light.whitewl
    };

    void set2ui_Swatch(int t) {
        Light light = canvasedlight.getFiltered((Dye)(availabledyes.elementAt(set_indexdyes[t])),
                set_dyeamounts[t]);
        ui_dyeswatches[t].setLight(light);
    }

    void set2ui_CombinedSwatch() {
        Light combinedlight = canvasedlight.getCopy();
        for (int t = 0; (t < nummixeddyes); t++) {
            if (set_dyeenables[t]) {
                combinedlight.filter((Dye)(availabledyes.elementAt(set_indexdyes[t])), set_dyeamounts[t]);
            }
        }
        if (set_combinedbright) {
            combinedlight.normalize();
        }
        ui_combinedswatch.setLight(combinedlight);
    }

    void set2ui_CanvasSwatch() {
        canvasedlight = light.getCopy();
        canvasedlight.filter((Canvas)(availablecanvases.elementAt(set_indexcanvas)), 1.0);
        if (set_canvaswhite) {
            canvasedlight.normalizeWhite();
        }
        if (set_canvasbright) {
            canvasedlight.normalize();
        }
        ui_canvasswatch.setLight(canvasedlight);
        for (int t = 0; (t < nummixeddyes); t++) {
            set2ui_Swatch(t);
        }
        set2ui_CombinedSwatch();
    }

    void set2ui_AllSwatches() {
        light = ((Light)(availablelights.elementAt(set_indexlight))).getCopy();
        if (set_lightwhite) {
            light.normalizeWhite();
        } else {
            light.normalize();
        }
        ui_lightswatch.setLight(light);
        set2ui_CanvasSwatch();
    }

    void set2ui_DyeSlider(int t) {
        listen_dyesliders[t].setDeaf(true);
        ui_dyesliders[t].setValue(dyeAmountToSlider(set_dyeamounts[t]));
    }

    void set2ui_DyeAmount(int t) {
        listen_dyeamounts[t].setDeaf(true);
        ui_dyeamounts[t].setText(""+set_dyeamounts[t]);
        ui_dyeamounts[t].setCaretPosition(666);
    }

    void set2ui_DyeChoices() {
        for (int t = 0; (t < nummixeddyes); t++) {
            ui_dyes[t].select(set_indexdyes[t]);
        }
    }

    void set2ui_LightChoice() {
        ui_light.select(set_indexlight);
    }

    void set2ui_CanvasChoice() {
        ui_canvas.select(set_indexcanvas);
    }

    // Implement event listeners in inner classes so that DyeMixer
    // itself need not publicly implement these interfaces.  Listening
    // to these events is an implementation detail.

    // Listens to ui_canvaswhite
    class CanvasWhiteListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            // White is toggled
            set_canvaswhite = ui_canvaswhite.getState();
            set2ui_CanvasSwatch();
        }
    }

    // Listens to ui_canvasbright
    class CanvasBrightListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            // White is toggled
            set_canvasbright = ui_canvasbright.getState();
            set2ui_CanvasSwatch();
        }
    }

    // Listens to ui_lightwhite
    class LightWhiteListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            // White is toggled
            set_lightwhite = ui_lightwhite.getState();
            set2ui_AllSwatches();
        }
    }

    // Listens to ui_combinedbright
    class CombinedBrightListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            // White is toggled
            set_combinedbright = ui_combinedbright.getState();
            set2ui_CombinedSwatch();
        }
    }

    // listens to ui_dyeenables[t]
    class DyeEnableListener implements ItemListener {
        int t;
        DyeEnableListener(int t) {
            this.t = t;
        }
        public void itemStateChanged(ItemEvent e) {
            set_dyeenables[t] = ui_dyeenables[t].getState();
            set2ui_CombinedSwatch();
        }
    }

    // Listens to ui_light
    class LightChoiceListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            // Light is changed
            set_indexlight = ui_light.getSelectedIndex();
            set2ui_AllSwatches();
        }
    }

    // Listens to ui_canvas
    class CanvasChoiceListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            // Canvas is changed
            set_indexcanvas = ui_canvas.getSelectedIndex();
            set2ui_CanvasSwatch();
        }
    }

    // Listens to ui_dyes[t]
    class DyeChoiceListener implements ItemListener {
        int t;
        DyeChoiceListener(int t) {
            this.t = t;
        }
        public void itemStateChanged(ItemEvent e) {
            set_indexdyes[t] = ui_dyes[t].getSelectedIndex();
            set2ui_Swatch(t);
            set2ui_CombinedSwatch();
        }
    }

    // Listens to ui_dyesliders[t]
    class DyeSliderListener implements AdjustmentListener {
        int t;
        boolean deaf = false;
        DyeSliderListener(int t) {
            this.t = t;
        }
        void setDeaf(boolean deaf) {
            this.deaf = deaf;
        }
        public void adjustmentValueChanged(AdjustmentEvent e) {
            if (!deaf) {
                set_dyeamounts[t] = dyeSliderToAmount(ui_dyesliders[t].getValue());
                set2ui_DyeAmount(t);
                set2ui_Swatch(t);
                set2ui_CombinedSwatch();
            } else {
                deaf = false;
            }
        }
    }

    // listens to ui_dyeamounts[t]
    class DyeAmountListener implements TextListener {
        int t;
        boolean deaf = false;
        DyeAmountListener(int t) {
            this.t = t;
        }
        void setDeaf(boolean deaf) {
            this.deaf = deaf;
        }
        public void textValueChanged(TextEvent e) {
            if (!deaf) {
                if ((ui_dyeamounts[t].getText().length() != 0) &&
                        !ui_dyeamounts[t].getText().equals(".") &&
                        !ui_dyeamounts[t].getText().equals("-") &&
                        !ui_dyeamounts[t].getText().equals("+")) {
                    try {
                        set_dyeamounts[t] = Double.valueOf(ui_dyeamounts[t].getText()).
                                doubleValue();
                        set2ui_DyeSlider(t);
                        set2ui_Swatch(t);
                        set2ui_CombinedSwatch();
                    } catch (NumberFormatException ex) {
                        set2ui_DyeAmount(t);
                    }
                }
            } else {
                deaf = false;
            }
        }
    }

    interface HasNumber {
        void setNumber(int number);
        int getNumber();
    }

    static class NumberFinder {
        static int indexcache = 0;

        // returns -1 if can't find
        static int findIndex(Vector v, int number) {
            int i = indexcache-1;
            if (i < 0) i = 0;
            if (i >= v.size()) i = 0;
            for(int t = 0; (t < v.size()); t++) {
                HasNumber owner = (HasNumber)(v.elementAt(i));
                if (number == owner.getNumber()) {
                    indexcache = i;
                    return i;
                }
                i++;
                if (i >= v.size()) i -= v.size();
            }
            return -1;
        }
    }

    static class StringCompare {
        // Returns <0, 0 or >0 if this object
        // is before, at or after the specified object when sorted.
        static int compare(String s1, String s2) {
            Reader r1 = new StringReader(s1);
            Reader r2 = new StringReader(s2);
            StreamTokenizer st1 = new StreamTokenizer(r1);
            StreamTokenizer st2 = new StreamTokenizer(r2);
            st1.lowerCaseMode(true);
            st2.lowerCaseMode(true);
            try {
                for(;;) {
                    int ttype1, ttype2;
                    for (;;) {
                        ttype1 = st1.nextToken();
                        if (ttype1 == StreamTokenizer.TT_EOF) break;
                        if (ttype1 == StreamTokenizer.TT_WORD) break;
                        if (ttype1 == StreamTokenizer.TT_NUMBER) break;
                    }
                    for (;;) {
                        ttype2 = st2.nextToken();
                        if (ttype2 == StreamTokenizer.TT_EOF) break;
                        if (ttype2 == StreamTokenizer.TT_WORD) break;
                        if (ttype2 == StreamTokenizer.TT_NUMBER) break;
                    }
                    if (ttype1 == StreamTokenizer.TT_EOF) {
                        if (ttype2 == StreamTokenizer.TT_EOF) return 0;
                        return -1;
                    }
                    if (ttype2 == StreamTokenizer.TT_EOF) return 1;
                    if (ttype1 == StreamTokenizer.TT_NUMBER) {
                        if (ttype2 == StreamTokenizer.TT_NUMBER) {
                            double diff = st1.nval - st2.nval;
                            if (diff != 0) {
                                if (diff < 0) return -1;
                                return 1;
                            }
                        } else return -1;
                    } else {
                        if (ttype2 == StreamTokenizer.TT_NUMBER) return 1;
                        if (st1.sval.equals("blank")) {
                            if (!st2.sval.equals("blank")) return -1;
                        } else if (st2.sval.equals("blank")) return 1;
                        else if (st1.sval.equals("reference")) {
                            if (!st2.sval.equals("reference")) return -1;
                        } else if (st2.sval.equals("reference")) return 1;
                        else if (st1.sval.equals("standard")) {
                            if (!st2.sval.equals("standard")) return -1;
                        } else if (st2.sval.equals("standard")) return 1;
                        else {
                            int diff = st1.sval.compareTo(st2.sval);
                            if (diff != 0) return diff;
                        }
                    }
                }
            } catch (Exception e) {
                return 0;
            }
            //		return s1.compareTo(s2);
        }
    }

    interface Sortable {
        // Returns <0, 0 or >0 if this object
        // is before, at or after the specified object.
        int compareTo(Sortable s);
    }

    static class QuickSort {
        static void sort(Vector a, int first0, int last0) {
            int first = first0;
            int last = last0;
            if (first >= last) {
                return;
            } else if( first == last - 1 ) {
                if (((Sortable)(a.elementAt(first))).compareTo((Sortable)(a.elementAt(last))) > 0) {
                    Object temp = a.elementAt(first);
                    a.setElementAt(a.elementAt(last), first);
                    a.setElementAt(temp, last);
                }
                return;
            }
            Sortable pivot = (Sortable)(a.elementAt((first + last) / 2));
            a.setElementAt(a.elementAt(last), (first + last) / 2);
            a.setElementAt(pivot, last);
            while( first < last ) {
                while ((((Sortable)(a.elementAt(first))).compareTo(pivot) <= 0) && (first < last)) {
                    first++;
                }
                while ((pivot.compareTo((Sortable)(a.elementAt(last))) <= 0) && (first < last)) {
                    last--;
                }
                if( first < last ) {
                    Object temp = a.elementAt(first);
                    a.setElementAt(a.elementAt(last), first);
                    a.setElementAt(temp, last);
                }
            }
            a.setElementAt(a.elementAt(last), last0);
            a.setElementAt(pivot, last);
            sort(a, first0, first-1);
            sort(a, last+1, last0);
        }

        static void sort(Vector a) {
            sort(a, 0, a.size()-1);
        }
    }

    static final Color backgroundcolor = new Color(230, 230, 230);

    static final int maxdyeslider = 512;
    static final double maxdyeamount = 30;

    static int dyeAmountToSlider(double amount) {
        int x = Math.round((float)Math.pow(amount/maxdyeamount, 1.0/3.0)*maxdyeslider);
        if (x < 0) x = 0;
        if (x > maxdyeslider) x = maxdyeslider;
        return x;
    }

    static double dyeSliderToAmount(int slider) {
        double x = slider/(double)maxdyeslider;
        x = x*x*x*maxdyeamount;
        x = Math.round(x*1000)/1000.0;
        return x;
    }

    double[] set_dyeamounts = new double[nummixeddyes];
    int set_indexlight; Light light, canvasedlight;
    int set_indexcanvas;
    int[] set_indexdyes = new int[nummixeddyes];
    boolean set_lightwhite = true;
    boolean set_canvaswhite = false;
    boolean set_canvasbright = false;
    boolean set_combinedbright = false;
    boolean set_dyeenables[] = new boolean[nummixeddyes];

    Choice ui_light;
    Choice ui_canvas;
    Checkbox[] ui_dyeenables = new Checkbox[nummixeddyes];
    Choice[] ui_dyes = new Choice[nummixeddyes];
    TextField[] ui_dyeamounts = new TextField[nummixeddyes];
    Swatch ui_lightswatch;
    Swatch ui_canvasswatch;
    Swatch[] ui_dyeswatches = new Swatch[nummixeddyes];
    Swatch ui_combinedswatch;
    Checkbox ui_lightwhite;
    Checkbox ui_canvaswhite;
    Checkbox ui_canvasbright;
    Checkbox ui_combinedbright;
    Scrollbar[] ui_dyesliders = new Scrollbar[nummixeddyes];

    DyeSliderListener[] listen_dyesliders = new DyeSliderListener[nummixeddyes];
    DyeAmountListener[] listen_dyeamounts = new DyeAmountListener[nummixeddyes];

    public DyeMixer() {
        GridBagConstraints c;
        GridBagLayout l;

        // load stuff

        String reason = "no reason";
        String dataname = "data.txt";

        availablelights = new Vector(generatedlights.length);
        availablecanvases = new Vector(generatedcanvases.length);
        availabledyes = new Vector(generateddyes.length);
        for (int t = 0; (t < generatedlights.length); t++) {
            availablelights.addElement(generatedlights[t]);
        }
        for (int t = 0; (t < generatedcanvases.length); t++) {
            availablecanvases.addElement(generatedcanvases[t]);
        }
        for (int t = 0; (t < generateddyes.length); t++) {
            availabledyes.addElement(generateddyes[t]);
        }
        int defaultlightnumber = 1;
        int defaultcanvasnumber = 1;
        int defaultdyenumber = 1;

        BufferedReader reader;
        StreamTokenizer tokenizer;

        if (!failed) try {
            InputStream stream = this.getClass().getClassLoader().getResourceAsStream(dataname);
            reader = new BufferedReader(new InputStreamReader(stream));
            tokenizer = new StreamTokenizer(reader);
            tokenizer.slashSlashComments(true);
            tokenizer.slashStarComments(true);
            final int parsestatenone = 0;
            final int parsestatelight = 1;
            final int parsestatecanvas = 2;
            final int parsestatedye = 3;
            final int spectrumtypenone = 0;
            final int spectrumtypeeven = 1;
            final int spectrumtypeuneven = 2;
            int parsestate = parsestatenone;
            int spectrumtype = spectrumtypenone;
            String name = "?";
            String category = "?";
            String ci = "?";
            String mixture = "?";
            double spectrumstart = 350, spectrumstep = 5;
            int number = 0;
            boolean useasdefault = false;
            final int maxnumspectrumsamples = 65536;
            int numspectrumsamples = 0;
            double[] spectrum = new double[maxnumspectrumsamples];
            double gain = 1.0;
            int ttype = tokenizer.nextToken();
            for(;;) {
                if ((ttype == StreamTokenizer.TT_WORD) && (tokenizer.sval.equals("break"))) break;
                if ((parsestate == parsestatelight)||(parsestate == parsestatecanvas)||(parsestate == parsestatedye)) {
                    if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("name"))) {
                        ttype = tokenizer.nextToken();
                        if (ttype != 61) {
                            failed = true;
                            reason = "Expecting \"=\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                        if (ttype != 34) {
                            failed = true;
                            reason = "Expecting name in double quotes in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        name = tokenizer.sval;
                        ttype = tokenizer.nextToken();
                        if (ttype != 59) {
                            failed = true;
                            reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                    } else if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("category"))) {
                        ttype = tokenizer.nextToken();
                        if (ttype != 61) {
                            failed = true;
                            reason = "Expecting \"=\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                        if (ttype != 34) {
                            failed = true;
                            reason = "Expecting category name in double quotes in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        category = tokenizer.sval;
                        ttype = tokenizer.nextToken();
                        if (ttype != 59) {
                            failed = true;
                            reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                    } else if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("mixture"))) {
                        ttype = tokenizer.nextToken();
                        if (ttype != 61) {
                            failed = true;
                            reason = "Expecting \"=\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                        if (ttype != 34) {
                            failed = true;
                            reason = "Expecting either \"mixture\" or \"pure\" in double quotes in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        mixture = tokenizer.sval;
                        ttype = tokenizer.nextToken();
                        if (ttype != 59) {
                            failed = true;
                            reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                    } else if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("ci"))) {
                        ttype = tokenizer.nextToken();
                        if (ttype != 61) {
                            failed = true;
                            reason = "Expecting \"=\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                        if (ttype != 34) {
                            failed = true;
                            reason = "Expecting color index (c.i.) in double quotes in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ci = tokenizer.sval;
                        ttype = tokenizer.nextToken();
                        if (ttype != 59) {
                            failed = true;
                            reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                    } else if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("start"))) {
                        ttype = tokenizer.nextToken();
                        if (ttype != 61) {
                            failed = true;
                            reason = "Expecting \"=\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                        if (ttype != StreamTokenizer.TT_NUMBER) {
                            failed = true;
                            reason = "Expecting numerical value in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        spectrumstart = tokenizer.nval;
                        ttype = tokenizer.nextToken();
                        if (ttype != 59) {
                            failed = true;
                            reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();

                    } else if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("number"))) {
                        ttype = tokenizer.nextToken();
                        if (ttype != 61) {
                            failed = true;
                            reason = "Expecting \"=\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                        if (ttype != StreamTokenizer.TT_NUMBER) {
                            failed = true;
                            reason = "Expecting numerical value in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        number = (int)(tokenizer.nval);
                        ttype = tokenizer.nextToken();
                        if (ttype != 59) {
                            failed = true;
                            reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();

                    } else if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("start"))) {
                        ttype = tokenizer.nextToken();
                        if (ttype != 61) {
                            failed = true;
                            reason = "Expecting \"=\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                        if (ttype != StreamTokenizer.TT_NUMBER) {
                            failed = true;
                            reason = "Expecting numerical value in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        spectrumstart = tokenizer.nval;
                        ttype = tokenizer.nextToken();
                        if (ttype != 59) {
                            failed = true;
                            reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();

                    } else if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("step"))) {
                        ttype = tokenizer.nextToken();
                        if (ttype != 61) {
                            failed = true;
                            reason = "Expecting \"=\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();
                        if (ttype != StreamTokenizer.TT_NUMBER) {
                            failed = true;
                            reason = "Expecting numerical value in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        spectrumstep = tokenizer.nval;
                        ttype = tokenizer.nextToken();
                        if (ttype != 59) {
                            failed = true;
                            reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        ttype = tokenizer.nextToken();

                    } else if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("evendata"))) {
                        if (spectrumtype != spectrumtypenone) {
                            failed = true;
                            reason = "Duplicate spectrum in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }

                        ttype = tokenizer.nextToken();
                        if (ttype != 61) {
                            failed = true;
                            reason = "Expecting \"=\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        for (;;) {
                            ttype = tokenizer.nextToken();
                            if (ttype == 59) break;
                            if (ttype != StreamTokenizer.TT_NUMBER) {
                                failed = true;
                                reason = "Expecting numerical value in line "+ tokenizer.lineno() +" in "+ dataname;
                                break;
                            }
                            spectrum[numspectrumsamples++] = tokenizer.nval;
                            if (numspectrumsamples >= maxnumspectrumsamples) break;
                            ttype = tokenizer.nextToken();
                            if (ttype != 44) {
                                if (ttype == 59) break;
                                failed = true;
                                reason = "Expecting \",\" or \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                                break;
                            }

                        }
                        if (failed) break;
                        spectrumtype = spectrumtypeeven;
                        ttype = tokenizer.nextToken();
                    } else if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("unevendata"))) {
                        if (spectrumtype != spectrumtypenone) {
                            failed = true;
                            reason = "Duplicate spectrum in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }

                        ttype = tokenizer.nextToken();
                        if (ttype != 61) {
                            failed = true;
                            reason = "Expecting \"=\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        for (;;) {
                            ttype = tokenizer.nextToken();
                            if (ttype == 59) break;
                            if (ttype != StreamTokenizer.TT_NUMBER) {
                                failed = true;
                                reason = "Expecting numerical value in line "+ tokenizer.lineno() +" in "+ dataname;
                                break;
                            }
                            spectrum[numspectrumsamples++] = tokenizer.nval;
                            if (numspectrumsamples >= maxnumspectrumsamples) break;
                            ttype = tokenizer.nextToken();
                            if (ttype != 44) {
                                if (ttype == 59) break;
                                failed = true;
                                reason = "Expecting \",\" or \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                                break;
                            }

                        }
                        if (failed) break;
                        if ((numspectrumsamples % 2) != 0) {
                            failed = true;
                            reason = "Unevendata must have even length in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        spectrumtype = spectrumtypeuneven;
                        ttype = tokenizer.nextToken();
                    } else if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("default"))) {
                        ttype = tokenizer.nextToken();
                        if (ttype != 59) {
                            failed = true;
                            reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }
                        useasdefault = true;
                        ttype = tokenizer.nextToken();
                    } else if ((ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("light") || tokenizer.sval.equals("canvas") || tokenizer.sval.equals("dye"))) || (ttype == StreamTokenizer.TT_EOF)) {
                        // Create light/canvas/dye
                        // -----------------------

                        if (spectrumtype == spectrumtypenone) {
                            failed = true;
                            reason = "Missing spectral data before line "+ tokenizer.lineno() +" in "+ dataname;
                            break;
                        }

                        double[] spectrumcopy = new double[numspectrumsamples];
                        for (int t = 0; (t < numspectrumsamples); t++) {
                            spectrumcopy[t] = spectrum[t];
                        }

                        Spectrum newspectrum;
                        if (spectrumtype == spectrumtypeeven) {
                            newspectrum = new EvenlySampledSpectrum(spectrumcopy, spectrumstart, spectrumstep);
                        } else {
                            newspectrum = new UnevenlySampledSpectrum(spectrumcopy);
                        }

                        if (parsestate == parsestatecanvas) {
                            if (useasdefault) defaultcanvasnumber = number;
                            if (NumberFinder.findIndex(availablecanvases, number) >= 0) {
                                failed = true;
                                reason = "Duplicate number "+ number +" before line "+ tokenizer.lineno() +" in "+ dataname;
                                break;
                            }
                            Canvas newcanvas = new Canvas(category+", "+name, newspectrum);
                            newcanvas.setNumber(number);

                            availablecanvases.addElement(newcanvas);
                        }

                        else if (parsestate == parsestatedye) {
                            newspectrum.clipNegatives();
                            newspectrum.normalizeAbsorbance();
                            if (useasdefault) defaultdyenumber = number;
                            if (NumberFinder.findIndex(availabledyes, number) >= 0) {
                                failed = true;
                                reason = "Duplicate number "+ number +" before line "+ tokenizer.lineno() +" in "+ dataname;
                                break;
                            }
                            Dye newdye = new Dye(name, ci, category, mixture, newspectrum);
                            newdye.setNumber(number);
                            availabledyes.addElement(newdye);
                        }

                        else if (parsestate == parsestatelight) {
                            if (useasdefault) defaultlightnumber = number;
                            if (NumberFinder.findIndex(availablelights, number) >= 0) {
                                failed = true;
                                reason = "Duplicate number "+ number +" before line "+ tokenizer.lineno() +" in "+ dataname;
                                break;
                            }
                            Light newlight = new Light(category+", "+name, newspectrum);
                            newlight.setNumber(number);
                            availablelights.addElement(newlight);
                        }

                        if (ttype == StreamTokenizer.TT_EOF) break;
                        parsestate = parsestatenone;
                    } else ttype = tokenizer.nextToken(); // it's an unknown token, ignore.
                } else { // parsestate == parsestatenone
                    if (ttype == StreamTokenizer.TT_EOF) break;
                    if (ttype == StreamTokenizer.TT_WORD && (tokenizer.sval.equals("light") || tokenizer.sval.equals("canvas") || tokenizer.sval.equals("dye"))) {
                        if (tokenizer.sval.equals("canvas")) {
                            ttype = tokenizer.nextToken();
                            if (ttype != 59) {
                                failed = true;
                                reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                                break;
                            }
                            number = 0;
                            category = "?";
                            name = "?";
                            spectrumstart = 350;
                            spectrumstep = 5;
                            numspectrumsamples = 0;
                            spectrumtype = spectrumtypenone;
                            useasdefault = false;
                            parsestate = parsestatecanvas;
                            ttype = tokenizer.nextToken();
                        } else if (tokenizer.sval.equals("dye")) {
                            ttype = tokenizer.nextToken();
                            if (ttype != 59) {
                                failed = true;
                                reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                                break;
                            }
                            number = 0;
                            category = "?";
                            name = "?";
                            mixture = "?";
                            ci = "?";
                            spectrumstart = 350;
                            spectrumstep = 5;
                            numspectrumsamples = 0;
                            spectrumtype = spectrumtypenone;
                            useasdefault = false;
                            parsestate = parsestatedye;
                            ttype = tokenizer.nextToken();
                        } else if (tokenizer.sval.equals("light")) {
                            ttype = tokenizer.nextToken();
                            if (ttype != 59) {
                                failed = true;
                                reason = "Expecting \";\" in line "+ tokenizer.lineno() +" in "+ dataname;
                                break;
                            }
                            number = 0;
                            category = "?";
                            name = "?";
                            spectrumstart = 350;
                            spectrumstep = 5;
                            numspectrumsamples = 0;
                            spectrumtype = spectrumtypenone;
                            useasdefault = false;
                            parsestate = parsestatelight;
                            ttype = tokenizer.nextToken();
                        }
                    } else {
                        failed = true;
                        reason = "Expecting one of {\"canvas\", \"light\", \"dye\"} in line "+ tokenizer.lineno() +" in "+ dataname;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            failed = true;
            reason = "Could not read "+dataname;
        }

        if (failed) {
            Label ui_errorlabel, ui_reasonlabel;
            add(ui_errorlabel = new Label("Error:"));
            add(ui_reasonlabel = new Label(reason));
            l = new GridBagLayout();
            this.setLayout(l);
            c = new GridBagConstraints();
            c.gridx = 0;
            c.anchor = GridBagConstraints.SOUTH;
            l.setConstraints(ui_reasonlabel, c);
            c.anchor = GridBagConstraints.NORTH;
            l.setConstraints(ui_reasonlabel, c);
            return;
        }

        // Sort choices
        QuickSort.sort(availablelights);
        QuickSort.sort(availablecanvases);
        QuickSort.sort(availabledyes);

        // Initial settings
        for (int t = 0; (t < nummixeddyes); t++) {
            set_indexdyes[t] = NumberFinder.findIndex(availabledyes, defaultdyenumber);
            set_dyeamounts[t] = 1.0;
            set_dyeenables[t] = true;
        }
        set_indexlight = NumberFinder.findIndex(availablelights, defaultlightnumber);
        set_indexcanvas = NumberFinder.findIndex(availablecanvases, defaultcanvasnumber);

        // UI
        setBackground(backgroundcolor);

        // In Applet:
        // +------+------+------+
        // |      |      |      |
        // |panel1|panel2|panel3|
        // |      |      |      |
        // +------+------+------+
        Panel ui_panel2 = new Panel(); add(ui_panel2);

        l = new GridBagLayout();
        this.setLayout(l);
        c = new GridBagConstraints();
        c.weightx = 1.0; c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(1, 1, 1, 1);
        l.setConstraints(ui_panel2, c);

        // In panel2:
        // +---------+
        // | 2panel1 |
        // +---------+
        // | 2panel2 |
        // +---------+
        // | 2panel3 |
        // +---------+
        // | 2panel4 |
        // +---------+
        Panel ui_2panel1 = new Panel(); ui_panel2.add(ui_2panel1);
        Panel ui_2panel2 = new Panel(); ui_panel2.add(ui_2panel2);
        Panel ui_2panel3 = new Panel(); ui_panel2.add(ui_2panel3);
        Panel ui_2panel4 = new Panel(); ui_panel2.add(ui_2panel4);

        l = new GridBagLayout();
        ui_panel2.setLayout(l);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        l.setConstraints(ui_2panel1, c);
        l.setConstraints(ui_2panel2, c);
        l.setConstraints(ui_2panel3, c);
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_2panel4, c);

        // In 2panel1: +----------------------+---------------------+
        //             |       21panel1       |      21panel2       |
        // In 2panel2: +----------------------+---------------------+
        //             |       22panel1       |      22panel2       |
        // In 2panel3: +--------+--------+----+---+--------+--------+
        //             |23panel1|23panel2|23panel3|23panel4|23panel5|
        // In 2panel4: +--------+--------+--------+--------+--------+
        //             |                  24panel1                  |
        //             +--------------------------------------------+
        //             |                                            |
        //             +--------------------------------------------+
        Panel ui_21panel1 = new Panel(); ui_2panel1.add(ui_21panel1);
        Panel ui_21panel2 = new Panel(); ui_2panel1.add(ui_21panel2);

        l = new GridBagLayout();
        ui_2panel1.setLayout(l);
        c = new GridBagConstraints();
        c.gridy = 0;
        c.weightx = 0.75; c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_21panel1, c);
        c.weightx = 0.25;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_21panel2, c);

        Panel ui_22panel1 = new Panel(); ui_2panel2.add(ui_22panel1);
        Panel ui_22panel2 = new Panel(); ui_2panel2.add(ui_22panel2);

        l = new GridBagLayout();
        ui_2panel2.setLayout(l);
        c = new GridBagConstraints();
        c.gridy = 0;
        c.weightx = 0.75; c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_22panel1, c);
        c.weightx = 0.25;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_22panel2, c);

        Panel ui_23panel2 = new Panel(); ui_2panel3.add(ui_23panel2);
        Panel ui_23panel1 = new Panel(); ui_2panel3.add(ui_23panel1);
        Panel ui_23panel3 = new Panel(); ui_2panel3.add(ui_23panel3);
        Panel ui_23panel4 = new Panel(); ui_2panel3.add(ui_23panel4);
        Panel ui_23panel5 = new Panel(); ui_2panel3.add(ui_23panel5);

        l = new GridBagLayout();
        ui_2panel3.setLayout(l);
        c = new GridBagConstraints();
        c.gridy = 0;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.NORTH;
        c.weightx = 0.1;
        l.setConstraints(ui_23panel2, c);
        c.weightx = 0.0;
        l.setConstraints(ui_23panel1, c);
        c.weightx = 0.75;
        l.setConstraints(ui_23panel3, c);
        c.weightx = 0.05;
        l.setConstraints(ui_23panel4, c);
        c.weightx = 0.1;
        l.setConstraints(ui_23panel5, c);

        Panel ui_24panel1 = new Panel(); ui_2panel4.add(ui_24panel1);

        l = new GridBagLayout();
        ui_2panel4.setLayout(l);
        c = new GridBagConstraints();
        c.gridy = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        l.setConstraints(ui_24panel1, c);

        // In 21panel1: In 21panel2:
        // +---------+---------+
        // |211panel1|         |
        // +---------+         |
        // |         |         |
        // +---------+---------+
        //
        // In 22panel1: In 22panel2:
        // +---------+---------+
        // |221panel1|         |
        // +---------+         |
        // |         |         |
        // +---------+---------+

        Panel ui_211panel1 = new Panel(); ui_21panel1.add(ui_211panel1);
        Panel ui_221panel1 = new Panel(); ui_22panel1.add(ui_221panel1);

        // Add components
        Panel ui_temppanel;
        Label ui_lightlabel;
        ui_211panel1.add(ui_lightlabel = new Label("Illuminant"));
        ui_211panel1.add(ui_lightwhite = new Checkbox("White adapt", set_lightwhite));

        l = new GridBagLayout();
        ui_211panel1.setLayout(l);
        c = new GridBagConstraints();
        c.gridy = 0;
        c.weightx = 0.1;
        c.fill = GridBagConstraints.HORIZONTAL;
        l.setConstraints(ui_lightlabel, c);
        c.weightx = 0.9;
        l.setConstraints(ui_lightwhite, c);

        ui_21panel1.add(ui_light = new Choice());

        l = new GridBagLayout();
        ui_21panel1.setLayout(l);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_211panel1, c);
        l.setConstraints(ui_light, c);

        Label ui_lightswatchlabel;
        ui_21panel2.add(ui_lightswatchlabel = new Label("Swatch"));
        ui_21panel2.add(ui_lightswatch = new Swatch());

        l = new GridBagLayout();
        ui_21panel2.setLayout(l);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_lightswatchlabel, c);
        l.setConstraints(ui_lightswatch, c);

        Label ui_canvaslabel;
        ui_221panel1.add(ui_canvaslabel = new Label("Canvas"));
        ui_221panel1.add(ui_canvaswhite = new Checkbox("White adapt", set_canvaswhite));
        ui_221panel1.add(ui_canvasbright = new Checkbox("Brightness adapt", set_canvasbright));

        l = new GridBagLayout();
        ui_221panel1.setLayout(l);
        c = new GridBagConstraints();
        c.gridy = 0;
        c.weightx = 0.1;
        c.fill = GridBagConstraints.HORIZONTAL;
        l.setConstraints(ui_canvaslabel, c);
        l.setConstraints(ui_canvaswhite, c);
        c.weightx = 0.8;
        l.setConstraints(ui_canvasbright, c);

        ui_22panel1.add(ui_canvas = new Choice());

        l = new GridBagLayout();
        ui_22panel1.setLayout(l);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_221panel1, c);
        l.setConstraints(ui_canvas, c);

        Label ui_canvasswatchlabel;
        ui_22panel2.add(ui_canvasswatchlabel = new Label("Swatch"));
        ui_22panel2.add(ui_canvasswatch = new Swatch());

        l = new GridBagLayout();
        ui_22panel2.setLayout(l);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_canvasswatchlabel, c);
        l.setConstraints(ui_canvasswatch, c);

        Label ui_dyemixlabel, ui_dyelabel, ui_dyeamountlabel, ui_dyeamountlabel2;
        Label ui_dyeswatchlabel;

        c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;

        ui_23panel1.add(ui_dyemixlabel = new Label("Mix"));

        l = new GridBagLayout();
        ui_23panel1.setLayout(l);
        l.setConstraints(ui_dyemixlabel, c);

        ui_23panel2.add(ui_dyelabel = new Label("Dye"));

        l = new GridBagLayout();
        ui_23panel2.setLayout(l);
        l.setConstraints(ui_dyelabel, c);

        ui_23panel3.add(ui_dyeamountlabel = new Label("Amount"));

        l = new GridBagLayout();
        ui_23panel3.setLayout(l);
        l.setConstraints(ui_dyeamountlabel, c);

        ui_23panel4.add(ui_dyeamountlabel2 = new Label(""));

        l = new GridBagLayout();
        ui_23panel4.setLayout(l);
        l.setConstraints(ui_dyeamountlabel2, c);

        ui_23panel5.add(ui_dyeswatchlabel = new Label("Swatch"));

        l = new GridBagLayout();
        ui_23panel5.setLayout(l);
        l.setConstraints(ui_dyeswatchlabel, c);

        for (int t = 0; (t < nummixeddyes); t++) {

            c = new GridBagConstraints();
            c.gridx = 0;
            c.weightx = 1.0;
            c.weighty = 1.0/nummixeddyes;
            c.fill = GridBagConstraints.HORIZONTAL;

            ui_23panel2.add(ui_dyes[t] = new Choice());
            ((GridBagLayout)(ui_23panel2.getLayout())).setConstraints(ui_dyes[t], c);

            ui_23panel1.add(ui_dyeenables[t] = new Checkbox("", set_dyeenables[t]));
            ((GridBagLayout)(ui_23panel1.getLayout())).setConstraints(ui_dyeenables[t], c);

            ui_23panel4.add(ui_dyeamounts[t] = new TextField(""+set_dyeamounts[t], 3));
            ((GridBagLayout)(ui_23panel4.getLayout())).setConstraints(ui_dyeamounts[t], c);

            ui_23panel3.add(ui_dyesliders[t] = new Scrollbar(Scrollbar.HORIZONTAL, dyeAmountToSlider(set_dyeamounts[t]), 5, 0, maxdyeslider+5));
            ui_dyesliders[t].setBackground(backgroundcolor);
            ((GridBagLayout)(ui_23panel3.getLayout())).setConstraints(ui_dyesliders[t], c);

            ui_23panel5.add(ui_dyeswatches[t] = new Swatch());
            ((GridBagLayout)(ui_23panel5.getLayout())).setConstraints(ui_dyeswatches[t], c);
        }

        Label ui_combinedlabel;
        ui_24panel1.add(ui_combinedlabel = new Label("Mixed Swatch"));
        ui_24panel1.add(ui_combinedbright = new Checkbox("Brightness adapt", set_combinedbright));
        l = new GridBagLayout();
        ui_24panel1.setLayout(l);
        c = new GridBagConstraints();
        c.gridy = 0;
        c.weightx = 0.1;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_combinedlabel, c);
        c.weightx = 0.9;
        l.setConstraints(ui_combinedbright, c);

        ui_2panel4.add(ui_combinedswatch = new Swatch());

        l = (GridBagLayout)ui_2panel4.getLayout();
        c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        l.setConstraints(ui_combinedswatch, c);

        // Set up choices
        for (int u = 0; (u < availablelights.size()); u++) {
            ui_light.add(((Light)(availablelights.elementAt(u))).getName());
        }
        set2ui_LightChoice();
        for (int u = 0; (u < availablecanvases.size()); u++) {
            ui_canvas.add(((Canvas)(availablecanvases.elementAt(u))).getName());
        }
        set2ui_CanvasChoice();
        for (int t = 0; (t < nummixeddyes); t++) {
            for (int u = 0; (u < availabledyes.size()); u++) {
                ui_dyes[t].add(((Dye)(availabledyes.elementAt(u))).getName());
            }
        }
        set2ui_DyeChoices();
        set2ui_AllSwatches();

        // Set up listeners
        ui_lightwhite.addItemListener(new LightWhiteListener());
        ui_canvaswhite.addItemListener(new CanvasWhiteListener());
        ui_canvasbright.addItemListener(new CanvasBrightListener());
        ui_combinedbright.addItemListener(new CombinedBrightListener());
        ui_light.addItemListener(new LightChoiceListener());
        ui_canvas.addItemListener(new CanvasChoiceListener());
        for (int t = 0; (t < nummixeddyes); t++) {
            ui_dyeenables[t].addItemListener(new DyeEnableListener(t));
            ui_dyes[t].addItemListener(new DyeChoiceListener(t));
            ui_dyeamounts[t].addTextListener(listen_dyeamounts[t] = new DyeAmountListener(t));
            ui_dyesliders[t].addAdjustmentListener(listen_dyesliders[t] = new DyeSliderListener(t));
        }

    }

    public void start() {
    }

    public void stop() {
    }

    public void destroy() {
    }

    public void paint(Graphics g) {
    }
}

// FOR EMACS!!!
// Local Variables:
// compile-command: "javac -target 1.1 DyeMixer.java"
// End:
