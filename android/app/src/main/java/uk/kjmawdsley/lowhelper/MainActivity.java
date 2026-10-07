package uk.kjmawdsley.lowhelper;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(11,16,32), CARD = Color.rgb(20,27,45), PANEL = Color.rgb(14,21,37);
    private static final int TEXT = Color.rgb(247,248,251), MUTED = Color.rgb(154,165,186), ACCENT = Color.rgb(127,140,255);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView glucose, d1, d5, d15, status, risk, eq, buffer, fast, slow, why;
    private EditText iob, icr, isf, target;
    private SharedPreferences prefs;

    static class Reading {
        long t; double g;
        Reading(long t, double g){ this.t=t; this.g=g; }
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        setContentView(buildUi());
        refresh();
    }

    private ScrollView buildUi(){
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(BG);
        LinearLayout root=col(20); root.setPadding(dp(18),dp(20),dp(18),dp(36)); scroll.addView(root);

        TextView title=t("Low Helper",30,TEXT,true); root.addView(title);
        TextView sub=t("Reads glucose directly from GlucoDataHandler on this phone. IOB stays manual and no glucose data is sent to the web.",14,MUTED,false);
        sub.setPadding(0,dp(4),0,dp(18)); root.addView(sub);

        LinearLayout input=card(); root.addView(input);
        input.addView(label("Current glucose"));
        glucose=t("—",28,TEXT,true); input.addView(glucose);
        status=t("Connecting to GDH…",13,MUTED,false); status.setPadding(0,dp(4),0,dp(14)); input.addView(status);

        input.addView(label("Insulin on board"));
        iob=number("1.0"); input.addView(iob);

        LinearLayout deltas=new LinearLayout(this); deltas.setOrientation(LinearLayout.HORIZONTAL); deltas.setPadding(0,dp(14),0,0);
        d1=deltaBox(deltas,"1 min"); d5=deltaBox(deltas,"5 min"); d15=deltaBox(deltas,"15 min"); input.addView(deltas);

        Button refresh=button("Refresh from GlucoDataHandler"); refresh.setOnClickListener(v->refresh()); input.addView(refresh);
        Button assess=button("Update assessment"); assess.setOnClickListener(v->assess()); input.addView(assess);

        LinearLayout result=card(); result.setPadding(dp(16),dp(16),dp(16),dp(18)); root.addView(result);
        result.addView(label("Current assessment")); risk=t("—",24,TEXT,true); result.addView(risk);
        LinearLayout metrics=new LinearLayout(this); metrics.setOrientation(LinearLayout.HORIZONTAL); metrics.setPadding(0,dp(14),0,0);
        eq=metric(metrics,"IOB carb-equivalent"); buffer=metric(metrics,"Buffer to target"); result.addView(metrics);
        result.addView(labelPad("Fast-carb emphasis",16)); fast=t("—",14,MUTED,false); result.addView(fast);
        result.addView(labelPad("Slower-carb emphasis",12)); slow=t("—",14,MUTED,false); result.addView(slow);
        why=t("",13,MUTED,false); why.setPadding(0,dp(14),0,0); result.addView(why);

        LinearLayout settings=card(); root.addView(settings);
        settings.addView(t("Settings",17,TEXT,true));
        settings.addView(labelPad("ICR — grams per unit",12)); icr=number(String.valueOf(prefs.getFloat("icr",6f))); settings.addView(icr);
        settings.addView(labelPad("ISF — mmol/L per unit",12)); isf=number(String.valueOf(prefs.getFloat("isf",3.7f))); settings.addView(isf);
        settings.addView(labelPad("Recovery target — mmol/L",12)); target=number(String.valueOf(prefs.getFloat("target",6.5f))); settings.addView(target);
        Button save=button("Save settings"); save.setOnClickListener(v->{ saveSettings(); assess(); Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show(); }); settings.addView(save);

        TextView foot=t("Experimental personal decision aid only. If you are actually hypo, use your established hypo treatment plan rather than this calculation.",12,MUTED,false);
        foot.setPadding(0,dp(2),0,0); root.addView(foot);
        return scroll;
    }

    private void refresh(){
        status.setText("Reading local GDH server…");
        executor.execute(()->{
            try{
                URL u=new URL("http://127.0.0.1:17580/sgv.json?count=24");
                HttpURLConnection c=(HttpURLConnection)u.openConnection();
                c.setConnectTimeout(2500); c.setReadTimeout(2500); c.setUseCaches(false);
                BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line); br.close();
                JSONArray arr=new JSONArray(sb.toString());
                List<Reading> rows=new ArrayList<>();
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.getJSONObject(i);
                    rows.add(new Reading(o.optLong("date"),toMmol(o.optDouble("sgv",Double.NaN))));
                }
                if(rows.isEmpty()||Double.isNaN(rows.get(0).g)) throw new Exception("No usable readings");
                Reading latest=rows.get(0);
                Reading prev=rows.size()>1?rows.get(1):null;
                Reading r5=nearest(rows,latest.t,5), r15=nearest(rows,latest.t,15);
                double one=0;
                if(prev!=null){
                    double mins=Math.max(0.5,(latest.t-prev.t)/60000.0);
                    one=(latest.g-prev.g)/mins;
                }
                final double fd1=one, fd5=r5==null?Double.NaN:latest.g-r5.g, fd15=r15==null?Double.NaN:latest.g-r15.g;
                runOnUiThread(()->{
                    glucose.setText(String.format(Locale.UK,"%.1f mmol/L",latest.g));
                    glucose.setTag(latest.g);
                    d1.setText(formatDelta(fd1)); d1.setTag(fd1);
                    d5.setText(formatDelta(fd5)); d5.setTag(fd5);
                    d15.setText(formatDelta(fd15)); d15.setTag(fd15);
                    status.setText("GDH connected · "+android.text.format.DateFormat.format("HH:mm",latest.t));
                    assess();
                });
            }catch(Exception e){
                runOnUiThread(()->status.setText("Couldn't read GDH. Check that Local Web Service is enabled in GlucoDataHandler."));
            }
        });
    }

    private void assess(){
        Object bgTag=glucose.getTag(); if(!(bgTag instanceof Double)){ risk.setText("Waiting for GDH"); return; }
        double bg=(Double)bgTag, insulin=parse(iob,0), ICR=parse(icr,6), ISF=parse(isf,3.7), tgt=parse(target,6.5);
        double a=tag(d1), b=tag(d5), c=tag(d15);
        double rate=(safe(c)/15)*.55+(safe(b)/5)*.35+safe(a)*.10;
        int ts=0; String trend="Stable";
        if(rate<=-.10 || (!Double.isNaN(c)&&c<=-1)){trend="Falling quickly";ts=3;}
        else if(rate<=-.035 || (!Double.isNaN(c)&&c<=-.4)){trend="Falling";ts=2;}
        else if(rate>=.06 || (!Double.isNaN(c)&&c>=.6)){trend="Rising";ts=-1;}
        else if(rate>.015){trend="Drifting up";}

        int bs=bg<4?5:bg<4.5?4:bg<5?3:bg<5.5?2:bg<=6?1:0;
        int is=insulin>=2?3:insulin>=1?2:insulin>=.4?1:0;
        int score=bs+ts+is;
        String r=bg<4?"Hypo — use your treatment plan":score>=7?"High downward pressure":score>=4?"Meaningful downward pressure":score>=2?"Watch closely":"Low immediate risk";
        risk.setText(r+" · "+trend);
        eq.setText(String.format(Locale.UK,"%.1fg",insulin*ICR));
        buffer.setText(String.format(Locale.UK,"%.1fg",Math.max(0,tgt-bg)*ICR/ISF));
        if(bg<4){ fast.setText("Do not use this model to determine hypo treatment."); slow.setText("Reassess after the immediate low is corrected."); }
        else{
            fast.setText(ts>=3?"High — immediate trend is the dominant signal.":ts>=2?"Moderate — sustained downward trend present.":"Low — no strong short-term drop signal.");
            slow.setText(insulin>=1.5&&ts>=1?"High ongoing insulin pressure.":insulin>=.7?"Moderate ongoing insulin pressure.":"Low — relatively little IOB remains.");
        }
        why.setText(bg<4?"The calculator deliberately steps back once glucose is below 4.0.":ts>=3&&insulin>=1?"Sustained downward momentum and meaningful IOB point the same way.":ts>=2?"The longer CGM windows support a genuine downward trend.":insulin>=1?"CGM is fairly calm, but meaningful IOB remains.":"Little evidence of strong downward pressure right now.");
    }

    private void saveSettings(){ prefs.edit().putFloat("icr",(float)parse(icr,6)).putFloat("isf",(float)parse(isf,3.7)).putFloat("target",(float)parse(target,6.5)).apply(); }
    private static double toMmol(double v){ return v>35?v/18.0:v; }
    private static Reading nearest(List<Reading> rows,long latest,int mins){
        long target=latest-mins*60000L,bestDiff=Long.MAX_VALUE; Reading best=null;
        for(Reading r:rows){ long d=Math.abs(r.t-target); if(d<bestDiff){bestDiff=d;best=r;} }
        long tolerance=Math.max(90000L,(long)(mins*60000L*.4)); return bestDiff<=tolerance?best:null;
    }
    private double tag(TextView v){ Object x=v.getTag(); return x instanceof Double?(Double)x:Double.NaN; }
    private double safe(double v){ return Double.isNaN(v)?0:v; }
    private String formatDelta(double v){ return Double.isNaN(v)?"—":String.format(Locale.UK,"%+.2f",v); }
    private double parse(EditText e,double f){ try{return Double.parseDouble(e.getText().toString());}catch(Exception x){return f;} }

    private LinearLayout col(int pad){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(pad),dp(pad),dp(pad),dp(pad)); return l; }
    private LinearLayout card(){ LinearLayout l=col(16); l.setBackgroundColor(CARD); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,dp(14)); l.setLayoutParams(p); return l; }
    private TextView t(String s,int sp,int color,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if(bold)v.setTypeface(null,1); v.setLineSpacing(0,1.15f); return v; }
    private TextView label(String s){ return t(s,13,MUTED,false); }
    private TextView labelPad(String s,int top){ TextView v=label(s); v.setPadding(0,dp(top),0,dp(6)); return v; }
    private EditText number(String value){ EditText e=new EditText(this); e.setText(value); e.setTextColor(TEXT); e.setTextSize(20); e.setSingleLine(true); e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); e.setBackgroundColor(PANEL); e.setPadding(dp(14),dp(12),dp(14),dp(12)); return e; }
    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(15); b.setAllCaps(false); b.setBackgroundColor(ACCENT); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52)); p.setMargins(0,dp(12),0,0); b.setLayoutParams(p); return b; }
    private TextView deltaBox(LinearLayout row,String label){ LinearLayout box=col(8); box.setGravity(Gravity.CENTER); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); p.setMargins(dp(3),0,dp(3),0); box.setLayoutParams(p); box.setBackgroundColor(PANEL); box.addView(t(label,12,MUTED,false)); TextView v=t("—",18,TEXT,true); v.setPadding(0,dp(4),0,0); box.addView(v); row.addView(box); return v; }
    private TextView metric(LinearLayout row,String label){ LinearLayout box=col(10); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); p.setMargins(dp(3),0,dp(3),0); box.setLayoutParams(p); box.setBackgroundColor(PANEL); box.addView(t(label,11,MUTED,false)); TextView v=t("—",22,TEXT,true); box.addView(v); row.addView(box); return v; }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
}
