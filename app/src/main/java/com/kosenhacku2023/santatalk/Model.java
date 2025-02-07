package com.kosenhacku2023.santatalk;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Model extends AppCompatActivity {
    Dictionary dic;

    public Controller controller;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private final String[] permissions = {android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private boolean permissionToRecordAccepted = false;


    private MediaRecorder mediaRecorder;
    File outputFile;
//    static final  String outputFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/speech.wav";

    public Context conText_main;

    public Model(Controller controller) {
        dic = new Dictionary();
        this.controller = controller;
    }

    void init(Context conText_main) {
        //TODO
        this.conText_main = conText_main;
    }
    boolean isRecording(){
        return mediaRecorder != null;
    }


    void requestPermissions(Activity activity, Context context, android.view.View view){
        //TODO
        conText_main = context;
        Log.d("requestPermissions","Pass");
        // マイクのパーミッションが許可されているか確認
        if (ContextCompat.checkSelfPermission(conText_main, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(conText_main, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            permissionToRecordAccepted = true;
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_RECORD_AUDIO_PERMISSION);
        }

        this.outputFile = new File(conText_main.getFilesDir(), "speech.wav");

        Button recordButton = view.findViewById(R.id.Record_cState_button);
        recordButton.setOnClickListener(v -> {
//                Log.d("Mode","HEYEHYE");
            controller.changeRecord();
        });
    }

    public boolean isPermissionToRecordAccepted() {
        return permissionToRecordAccepted;
    }

    void recordStart(){
        //TODO
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);

        mediaRecorder.setOutputFile(outputFile);

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(conText_main, "録音の準備中にエラーが発生しました", Toast.LENGTH_SHORT).show();
            return;
        }

        mediaRecorder.start();
        Toast.makeText(conText_main, "録音開始", Toast.LENGTH_SHORT).show();

    }

    void recordStop(){
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Toast.makeText(conText_main, "録音終了", Toast.LENGTH_SHORT).show();
        }
    }
    //DEBUG
    public void playRecording() {
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(outputFile.getPath());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    List<double[]> audioDispatchToPhoneme(double[] rawData){
        final int windowSize = 1;
        List<double[]> result = new ArrayList<>();
        double[] tmp;
        double power = 0;
        int index = 0;
        boolean cutFlag = false;

        for (double rawDatum : rawData) {
            power += Math.abs(rawDatum);
        }
        power /= rawData.length;

        for(int i = 0; i < rawData.length; i+=windowSize) {
            double localPower = 0;
            for (int j = 0; j < windowSize; j++) {
                localPower += Math.abs(rawData[windowSize * i + j]);
            }
            if(cutFlag && localPower <= power){
                tmp = new double[rawData.length];
                System.arraycopy(rawData, index, tmp, 0, i - index);
                result.add(tmp);
                cutFlag = false;
            }
            if(!cutFlag && localPower > power){
                index = i;
                cutFlag = true;
            }
        }
        return result;
    }

    public static double[] normalize(int[] speech) {
        int maxValue = Arrays.stream(speech).max().orElse(Integer.MIN_VALUE);
        int minValue = Arrays.stream(speech).min().orElse(Integer.MAX_VALUE);
        int baseValue = Math.max(Math.abs(maxValue), Math.abs(minValue));

        // normalize to -1 ~ 1
        double[] normalizedArray = new double[speech.length];
        for (int i = 0; i < speech.length; i++) {
            double normalizedValue = speech[i] / (double) baseValue;
            normalizedArray[i] = normalizedValue;
        }
        return normalizedArray;
    }

    static String[] dispatchToWords(String str) {
        int wordIndex = 0;
        int pointer = 0;
        String[] text = new String[5];
        for(int i = 0; i < str.length(); i++) {
            if(str.charAt(i) == ' ') {
                if(wordIndex == 5) {
                    throw new RuntimeException("Too many words in the sentence.");
                }
                text[wordIndex++] = str.substring(pointer, i);
                pointer = i+1;
            }
        }
        text[wordIndex] = str.substring(pointer);
        return text;
    }

    static String connectToSentence(List<String> textList) {
        StringBuilder sentence = new StringBuilder();
        for(int i = 0; i < textList.size()-1; i++) {
            sentence.append(textList.get(i)).append(' ');
        }
        sentence.append(textList.get(textList.size()-1));
        return sentence.toString();
    }

    List<String> translateSJtoSS(String[] str){
        List<String> returnString = new ArrayList<>();
        for(String word : str){
            if(word == null) break;
            returnString.add(dic.JapanToSanta.get(word));
        }

        //Grammar check
        int index = 0;
        boolean isBadGrammar = false;

        if(dic.NounJapanToSanta.get(str[index]) != null){
            index++;
            if(dic.AuxVerbJapanToSanta.get(str[index])!= null) {
                index++;
            }

            if(dic.IntVerbJapanToSanta.get(str[index])!= null) {
                index++;
            }else if(dic.TranVerbJapanToSanta.get(str[index])!= null) {
                index++;
                if(dic.NounJapanToSanta.get(str[index])!= null)
                    index++;
                else
                    isBadGrammar = true;
            }else if(dic.AdjectiveJapanToSanta.get(str[index])!= null) {
                index++;
            }else{
                isBadGrammar = true;
            }
            if(dic.NumeralJapanToSanta.get(str[index])!= null) {
                index++;
            }
        }else if(dic.InterjectionJapanToSanta.get(str[index])!= null){
            index++;
        }else{
            isBadGrammar = true;
        }

        if(index != returnString.size()) isBadGrammar = true;
        if(isBadGrammar) returnString.add("(文法エラー)");

        return returnString;
    }

    List<String> translateSStoNJ(String[] str){ // Santa(Santa) to Natural(Japan)
        List<String> returnString = new ArrayList<>();

        for(String word : str){
            if(word == null) break;
            returnString.add(dic.SantaToJapan.get(word));
        }

        //Grammar check
        int index = 0;
        int particleIndex = 0;
        String buffer;
        boolean isFutureTense = false, isPastTense = false, isBadGrammar = false;

        if(dic.NounSantaToJapan.get(str[index]) != null){
            returnString.add(1, "は");
            particleIndex++;
            index++;
            if((buffer = dic.AuxVerbSantaToJapan.get(str[index]))!= null) {
                if (buffer.equals("だろう(したい)"))
                    isFutureTense = true;
                else if (buffer.equals("だった"))
                    isPastTense = true;
                index++;
                returnString.remove(buffer);
            }

            if((buffer = dic.IntVerbSantaToJapan.get(str[index]))!= null) {
                if (isFutureTense) {
                    returnString.set(returnString.indexOf(buffer),
                            dic.IntVerbFutureSantaToJapan.get(str[index]));
                }
                if (isPastTense) {
                    returnString.set(returnString.indexOf(buffer),
                            dic.IntVerbPastSantaToJapan.get(str[index]));
                }
                index++;
            }else if((buffer = dic.TranVerbSantaToJapan.get(str[index]))!= null) {
                switch (buffer){
                    case "行く":
                    case "与える":
                    case "感謝する":
                        returnString.add(1, "に");
                        break;
                    case "見る":
                    case "食べる":
                    case "置く":
                    case "作る":
                    case "かく":
                        returnString.add(1, "を");
                        break;
                    case "話す":
                        returnString.add(1, "と");
                        break;
                }
                particleIndex++;
                if (isFutureTense) {
                    returnString.set(returnString.indexOf(buffer),
                            dic.TranVerbFutureSantaToJapan.get(str[index]));
                }
                if (isPastTense) {
                    returnString.set(returnString.indexOf(buffer),
                            dic.TranVerbPastSantaToJapan.get(str[index]));
                }
                if((buffer = dic.NounSantaToJapan.get(str[index]))!= null) {
                    returnString.remove(buffer);
                    returnString.add(0, buffer);
                    index++;
                }else
                    isBadGrammar = true;
                index++;
            }else if(dic.AdjectiveSantaToJapan.get(str[index])!= null) {
                index++;
            }else{
                isBadGrammar = true;
            }
            if((buffer = dic.NumeralSantaToJapan.get(str[index]))!= null) {
                returnString.remove(buffer);
                returnString.add(1, buffer);
                index++;
            }
            if(isFutureTense) {
                returnString.add("だろう(したい)");
                particleIndex++;
            }
            if(isPastTense) {
                returnString.add("だった");
                particleIndex++;
            }
        }else if(dic.InterjectionSantaToJapan.get(str[index])!= null){
            index++;
        }else{
            isBadGrammar = true;
        }

        if(index+particleIndex != returnString.size()) isBadGrammar = true;
        if(isBadGrammar) returnString.add("(文法エラー)");

        return returnString;
    }
//    List<String> translateNJtoSJ(String[] str) { // Natural(Japan) to Santa(Japan)
//        int index = 0;
//        List<String> returnString = new ArrayList<String>();
//        String buffer;
//        int flagFuture = 0, flagPast = 0, flagGrammar = 0;
//        String Numeral;
//
//        if (dic.NounJapanToSanta.get(str[index]) != null) {
//            returnString.add(str[index]);
//            index++;
//            returnString.add("AuxVerb"); // 助動詞確保
//        } else if (dic.InterjectionJapanToSanta.get(str[index]) != null) {
//            returnString.add(str[index]);
//            index++;
//            flagGrammar = 1;
//            if (flagGrammar != 1)
//                returnString.add("(文法エラー)");
//            return returnString;
//        } else {
//            System.out.println("Noun_or_Interjection_error:" + str[index]);
//            if (flagGrammar != 1)
//                returnString.add("(文法エラー)");
//            return returnString;
//        }
//        if (index >= str.length) {
//            if (flagGrammar == 1) {
//                if (flagFuture == 1) {
//                    returnString.set(returnString.indexOf("AuxVerb"), "だろう(したい)");
//                } else if (flagPast == 1) {
//                    returnString.set(returnString.indexOf("AuxVerb"), "だった");
//                } else {
//                    returnString.set(returnString.indexOf("AuxVerb"), "");
//                }
//            } else {
//                return null;
//            }
//            return returnString;
//        }
//        if (str[index].equals("は")) {
//            index++;
//        } else {
//            System.out.println("は_error:" + str[index]);
//            if (flagGrammar == 1) {
//                if (flagFuture == 1) {
//                    returnString.set(returnString.indexOf("AuxVerb"), "だろう(したい)");
//                } else if (flagPast == 1) {
//                    returnString.set(returnString.indexOf("AuxVerb"), "だった");
//                } else {
//                    returnString.set(returnString.indexOf("AuxVerb"), "");
//                }
//            } else {
//
//            }
//            return returnString;
//        }
//        if (index >= str.length) {
//            if (flagGrammar == 1) {
//                if (flagFuture == 1) {
//                    returnString.set(returnString.indexOf("AuxVerb"), "だろう(したい)");
//                } else if (flagPast == 1) {
//                    returnString.set(returnString.indexOf("AuxVerb"), "だった");
//                } else {
//                    returnString.set(returnString.indexOf("AuxVerb"), "");
//                }
//            } else {
//
//            }
//            return returnString;
//        }
//        if (dic.NumeralJapanToSanta.get(str[index]) != null) {
//            Numeral = str[index];
//            index++;
//        } else {
//            Numeral = "";
//        }
//        if (index >= str.length) {
//            if (flagGrammar == 1) {
//                if (flagFuture == 1) {
//                    returnString.set(returnString.indexOf("AuxVerb"), "だろう(したい)");
//                } else if (flagPast == 1) {
//                    returnString.set(returnString.indexOf("AuxVerb"), "だった");
//                } else {
//                    returnString.set(returnString.indexOf("AuxVerb"), "");
//                }
//                returnString.add(Numeral);
//            } else {
//                returnString.add("(文法エラー)");
//            }
//            return returnString;
//        }
//        if ((buffer = dic.IntVerbJapanToSanta.get(str[index])) != null) {
//            returnString.add(str[index]);
//            index++;
//            flagGrammar = 1;
//        } else if ((buffer = dic.IntVerbFutureJapanToSanta.get(str[index])) != null) {
//            returnString.add(dic.IntVerbSantaToJapan.get(buffer));
//            index++;
//            flagGrammar = 1;
//            flagFuture = 1;
//        } else if ((buffer = dic.IntVerbPastJapanToSanta.get(str[index])) != null) {
//            returnString.add(dic.IntVerbSantaToJapan.get(buffer));
//            index++;
//            flagGrammar = 1;
//            flagPast = 1;
//        } else if (dic.NounJapanToSanta.get(str[index]) != null) {
//            returnString.add("TranVerb");
//            returnString.add(str[index]);
//            index++;
//            if (index >= str.length) {
//                if (flagGrammar == 1) {
//                    if (flagFuture == 1) {
//                        returnString.set(returnString.indexOf("AuxVerb"), "だろう(したい)");
//                    } else if (flagPast == 1) {
//                        returnString.set(returnString.indexOf("AuxVerb"), "だった");
//                    } else {
//                        returnString.set(returnString.indexOf("AuxVerb"), "");
//                    }
//                    returnString.add(Numeral);
//                } else {
//                    returnString.add("(文法エラー)");
//                }
//                return returnString;
//            }
//            if (str[index].equals("に") || str[index].equals("を") || str[index].equals("と")) {
//                index++;
//            } else if (str[index].equals("だ") || str[index].equals("だろう") || str[index].equals("だった")
//                    || str[index].equals("する")
//                    || str[index].equals("するだろう(したい)") || str[index].equals("した")) {
//            } else {
//                System.out.println("Particle_error:" + str[index]);
//                if (flagGrammar == 1) {
//                    if (flagFuture == 1) {
//                        returnString.set(returnString.indexOf("AuxVerb"), "だろう(したい)");
//                    } else if (flagPast == 1) {
//                        returnString.set(returnString.indexOf("AuxVerb"), "だった");
//                    } else {
//                        returnString.set(returnString.indexOf("AuxVerb"), "");
//                    }
//                    returnString.add(Numeral);
//                } else {
//                    returnString.add("(文法エラー)");
//                }
//                return returnString;
//            }
//            if (index >= str.length) {
//                if (flagGrammar == 1) {
//                    if (flagFuture == 1) {
//                        returnString.set(returnString.indexOf("AuxVerb"), "だろう(したい)");
//                    } else if (flagPast == 1) {
//                        returnString.set(returnString.indexOf("AuxVerb"), "だった");
//                    } else {
//                        returnString.set(returnString.indexOf("AuxVerb"), "");
//                    }
//                    returnString.add(Numeral);
//                } else {
//                    returnString.add("(文法エラー)");
//                }
//                return returnString;
//            }
//            if ((buffer = dic.TranVerbJapanToSanta.get(str[index])) != null) {
//                returnString.set(returnString.indexOf("TranVerb"), dic.TranVerbSantaToJapan.get(buffer));
//                index++;
//                flagGrammar = 1;
//            } else if ((buffer = dic.TranVerbFutureJapanToSanta.get(str[index])) != null) {
//                returnString.set(returnString.indexOf("TranVerb"), dic.TranVerbSantaToJapan.get(buffer));
//                index++;
//                flagGrammar = 1;
//                flagFuture = 1;
//            } else if ((buffer = dic.TranVerbPastJapanToSanta.get(str[index])) != null) {
//                returnString.set(returnString.indexOf("TranVerb"), dic.TranVerbSantaToJapan.get(buffer));
//                index++;
//                flagGrammar = 1;
//                flagPast = 1;
//            } else {
//                System.out.println("Tran_error:" + str[index]);
//                if (flagGrammar == 1) {
//                    if (flagFuture == 1) {
//                        returnString.set(returnString.indexOf("AuxVerb"), "だろう(したい)");
//                    } else if (flagPast == 1) {
//                        returnString.set(returnString.indexOf("AuxVerb"), "だった");
//                    } else {
//                        returnString.set(returnString.indexOf("AuxVerb"), "");
//                    }
//                    returnString.add(Numeral);
//                } else {
//                    returnString.add("(文法エラー)");
//                }
//                return returnString;
//            }
//        } else if (dic.AdjectiveJapanToSanta.get(str[index]) != null) {
//            returnString.add(str[index]);
//            index++;
//            flagGrammar = 1;
//        } else {
//            System.out.println("IntVerb_or_Noun_or_Adjective_error:" + str[index]);
//            if (flagGrammar == 1) {
//                if (flagFuture == 1) {
//                    returnString.set(returnString.indexOf("AuxVerb"), "だろう(したい)");
//                } else if (flagPast == 1) {
//                    returnString.set(returnString.indexOf("AuxVerb"), "だった");
//                } else {
//                    returnString.set(returnString.indexOf("AuxVerb"), "");
//                }
//                returnString.add(Numeral);
//            } else {
//                returnString.add("(文法エラー)");
//            }
//            return returnString;
//        }
//        if (flagFuture == 1) {
//            returnString.set(returnString.indexOf("AuxVerb"), "だろう(したい)");
//        } else if (flagPast == 1) {
//            returnString.set(returnString.indexOf("AuxVerb"), "だった");
//        } else {
//            returnString.set(returnString.indexOf("AuxVerb"), "");
//        }
//        returnString.add(Numeral);
//        if (flagGrammar != 1)
//            returnString.add("(文法エラー)");
//        return returnString;
//    }
}