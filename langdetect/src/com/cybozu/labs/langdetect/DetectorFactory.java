package com.cybozu.labs.langdetect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.ajax.JSON.ReaderSource;
import org.eclipse.jetty.util.ajax.JSON.StringSource;

import com.cybozu.labs.langdetect.util.LangProfile;

/**
 * Language Detector Factory Class
 * 
 * This class manages an initialization and constructions of {@link Detector}. 
 * 
 * Before using language detection library, 
 * load profiles with {@link DetectorFactory#loadProfile(String)} method
 * and set initialization parameters.
 * 
 * When the language detection,
 * construct Detector instance via {@link DetectorFactory#create()}.
 * See also {@link Detector}'s sample code.
 * 
 * <ul>
 * <li>4x faster improvement based on Elmer Garduno's code. Thanks!</li>
 * </ul>
 * 
 * @see Detector
 * @author Nakatani Shuyo
 */
public class DetectorFactory {
    public HashMap<String, double[]> wordLangProbMap;
    public ArrayList<String> langlist;
    public Long seed = null;
    private DetectorFactory() {
        wordLangProbMap = new HashMap<String, double[]>();
        langlist = new ArrayList<String>();
    }
    static private DetectorFactory instance_ = new DetectorFactory();

    /**
     * Load profiles from specified directory.
     * This method must be called once before language detection.
     *  
     * @param profileDirectory profile directory path
     * @throws LangDetectException  Can't open profiles(error code = {@link ErrorCode#FileLoadError})
     *                              or profile's format is wrong (error code = {@link ErrorCode#FormatError})
     */
    public static void loadProfile(String profileDirectory) throws LangDetectException {
        loadProfile(new File(profileDirectory));
    }

    private static LangProfile makeProfile(HashMap profileData) {
        HashMap<String, Long> freqData = (HashMap) profileData.get("freq");
        Object[] n_wordsData = (Object[]) profileData.get("n_words");
        HashMap freq = new HashMap<String, Integer>();
        for (Map.Entry<String, Long> entry : freqData.entrySet()) {
            freq.put(entry.getKey(), entry.getValue().intValue());
        }
        int[] n_words = new int[n_wordsData.length];
        for (int i = 0; i < n_wordsData.length; i++) {
            n_words[i] = ((Long) n_wordsData[i]).intValue();
        }
        return new LangProfile((String) profileData.get("name"), freq, n_words);
    }

    /**
     * Load profiles from specified directory.
     * This method must be called once before language detection.
     *  
     * @param profileDirectory profile directory path
     * @throws LangDetectException  Can't open profiles(error code = {@link ErrorCode#FileLoadError})
     *                              or profile's format is wrong (error code = {@link ErrorCode#FormatError})
     */
    public static void loadProfile(File profileDirectory) throws LangDetectException {
        File[] listFiles = profileDirectory.listFiles();
        if (listFiles == null)
            throw new LangDetectException(ErrorCode.NeedLoadProfileError, "Not found profile: " + profileDirectory);
            
        int langsize = listFiles.length, index = 0;
        for (File file: listFiles) {
            if (file.getName().startsWith(".") || !file.isFile()) continue;
            FileInputStream is = null;
            try {
                is = new FileInputStream(file);
                HashMap profileData = (HashMap<String, Object>)
                    (new JSON()).parse(new ReaderSource(
                                new BufferedReader(new InputStreamReader(is))));
                LangProfile profile = makeProfile(profileData);
                addProfile(profile, index, langsize);
                ++index;
            } catch (IOException e) {
                throw new LangDetectException(ErrorCode.FileLoadError, "can't open '" + file.getName() + "'");
            } finally {
                try {
                    if (is!=null) is.close();
                } catch (IOException e) {}
            }
        }
    }

    /**
     * Load profiles from specified directory.
     * This method must be called once before language detection.
     *  
     * @param profileDirectory profile directory path
     * @throws LangDetectException  Can't open profiles(error code = {@link ErrorCode#FileLoadError})
     *                              or profile's format is wrong (error code = {@link ErrorCode#FormatError})
     */
    public static void loadProfile(List<String> json_profiles) throws LangDetectException {
        int index = 0;
        int langsize = json_profiles.size();
        if (langsize < 2)
            throw new LangDetectException(ErrorCode.NeedLoadProfileError, "Need more than 2 profiles");
            
        for (String json: json_profiles) {
            HashMap profileData = (HashMap<String, Object>)
                (new JSON()).parse(new StringSource(json));
            LangProfile profile = makeProfile(profileData);
            addProfile(profile, index, langsize);
            ++index;
        }
    }

    /**
     * @param profile
     * @param langsize 
     * @param index 
     * @throws LangDetectException 
     */
    static /* package scope */ void addProfile(LangProfile profile, int index, int langsize) throws LangDetectException {
        String lang = profile.name;
        if (instance_.langlist.contains(lang)) {
            throw new LangDetectException(ErrorCode.DuplicateLangError, "duplicate the same language profile");
        }
        instance_.langlist.add(lang);
        for (String word: profile.freq.keySet()) {
            if (!instance_.wordLangProbMap.containsKey(word)) {
                instance_.wordLangProbMap.put(word, new double[langsize]);
            }
            int length = word.length();
            if (length >= 1 && length <= 3) {
                double prob = profile.freq.get(word).doubleValue() / profile.n_words[length - 1];
                instance_.wordLangProbMap.get(word)[index] = prob;
            }
        }
    }

    /**
     * Clear loaded language profiles (reinitialization to be available)
     */
    static public void clear() {
        instance_.langlist.clear();
        instance_.wordLangProbMap.clear();
    }

    /**
     * Construct Detector instance
     * 
     * @return Detector instance
     * @throws LangDetectException 
     */
    static public Detector create() throws LangDetectException {
        return createDetector();
    }

    /**
     * Construct Detector instance with smoothing parameter 
     * 
     * @param alpha smoothing parameter (default value = 0.5)
     * @return Detector instance
     * @throws LangDetectException 
     */
    public static Detector create(double alpha) throws LangDetectException {
        Detector detector = createDetector();
        detector.setAlpha(alpha);
        return detector;
    }

    static private Detector createDetector() throws LangDetectException {
        if (instance_.langlist.size()==0)
            throw new LangDetectException(ErrorCode.NeedLoadProfileError, "need to load profiles");
        Detector detector = new Detector(instance_);
        return detector;
    }
    
    public static void setSeed(long seed) {
        instance_.seed = seed;
    }
    
    public static final List<String> getLangList() {
        return Collections.unmodifiableList(instance_.langlist);
    }
}
