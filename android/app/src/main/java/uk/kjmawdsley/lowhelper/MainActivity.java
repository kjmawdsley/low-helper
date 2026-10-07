package uk.kjmawdsley.lowhelper;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
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
    private static final int BG = Color.rgb(9,13,24);
    private static final int SURFACE = Color.rgb(18,24,39);
    private static final int SURFACE_2 = Color.rgb(13,19,33);
    private static final int TEXT = Color.rgb(248,249,252);
    private static final int MUTED = Color.rgb(147,158,179);
    private static final int ACCENT = Color.rgb(134,143,255);
    private static final int ACCENT_SOFT = Color.rgb(36,42,73);
    private static final int GOOD = Color.rgb(42,148,107);
    private static final int WATCH = Color.rgb(56,97,172);
    private static final int WARN = Color.rgb(183,113,31);
    private static final int BAD = Color.rgb(178,58,64);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private TextView glucose, status, d1, d5, d15;
    private EditText iob;
    private TextView actionTitle, actionBody, detailLine, carbEq, buffer;
    private LinearLayout actionCard;

    static class Reading {
        long t; double g;
        Reading(long t,double g){this.t=t;this.g=g;}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        setContentView(buildUi());
        refresh();
    }

    @Override protected void onResume(){
        super.onResume();
        assess();
    }

    private ScrollView buildUi(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root=column();
        root.setPadding(dp(18),dp(18),dp(18),dp(34));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top=insets.getInsets(WindowInsets.Type.statusBars()).top;
            int bottom=insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            v.setPadding(dp(18),dp(18)+top,dp(18),dp(34)+bottom);
            return insets;
        });
        scroll.addView(root);

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleWrap=column();
        LinearLayout.LayoutParams twp=new LinearLayout.LayoutParams(0,-2,1);
        titleWrap.setLayoutParams(twp);
        titleWrap.addView(text("Low Helper",30,TEXT,true));
        TextView kicker=text("GDH + Omnipod IOB",12,MUTED,false);
        kicker.setPadding(0,dp(2),0,0);
        titleWrap.addView(kicker);
        top.addView(titleWrap);

        Button settings=smallButton("Settings");
        settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        top.addView(settings);
        root.addView(top);

        LinearLayout hero=surface();
        LinearLayout.LayoutParams hp=(LinearLayout.LayoutParams)hero.getLayoutParams();
        hp.setMargins(0,dp(18),0,dp(12));
        hero.setLayoutParams(hp);
        root.addView(hero);

        LinearLayout heroTop=new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout gWrap=column();
        LinearLayout.LayoutParams gwp=new LinearLayout.LayoutParams(0,-2,1);
        gWrap.setLayoutParams(gwp);
        gWrap.addView(overline("LIVE GLUCOSE"));
        glucose=text("—",42,TEXT,true);
        gWrap.addView(glucose);
        status=text("Connecting to GDH…",12,MUTED,false);
        status.setPadding(0,dp(3),0,0);
        gWrap.addView(status);
        heroTop.addView(gWrap);

        Button refresh=iconButton("↻");
        refresh.setOnClickListener(v->refresh());
        heroTop.addView(refresh);
        hero.addView(heroTop);

        LinearLayout deltaRow=new LinearLayout(this);
        deltaRow.setOrientation(LinearLayout.HORIZONTAL);
        deltaRow.setPadding(0,dp(18),0,0);
        d1=deltaPill(deltaRow,"1 min");
        d5=deltaPill(deltaRow,"5 min");
        d15=deltaPill(deltaRow,"15 min");
        hero.addView(deltaRow);

        LinearLayout iobCard=surface();
        root.addView(iobCard);
        iobCard.addView(overline("OMNIPOD IOB"));

        LinearLayout stepper=new LinearLayout(this);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.setPadding(0,dp(10),0,0);

        Button minus=stepButton("−");
        minus.setOnClickListener(v->stepIob(-0.1));
        stepper.addView(minus);

        iob=numberInput("");
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,dp(58),1);
        ip.setMargins(dp(10),0,dp(10),0);
        iob.setLayoutParams(ip);
        iob.setGravity(Gravity.CENTER);
        iob.setTextSize(28);
        iob.setHint("0.0");
        iob.setHintTextColor(Color.rgb(78,88,110));
        iob.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){assess();}
            public void afterTextChanged(Editable e){}
        });
        stepper.addView(iob);

        Button plus=stepButton("+");
        plus.setOnClickListener(v->stepIob(0.1));
        stepper.addView(plus);
        iobCard.addView(stepper);

        TextView unit=text("units on board",12,MUTED,false);
        unit.setGravity(Gravity.CENTER);
        unit.setPadding(0,dp(5),0,0);
        iobCard.addView(unit);

        LinearLayout chips=new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER);
        chips.setPadding(0,dp(12),0,0);
        addPreset(chips,0.5);
        addPreset(chips,1.0);
        addPreset(chips,1.5);
        addPreset(chips,2.0);
        iobCard.addView(chips);

        actionCard=column();
        actionCard.setPadding(dp(18),dp(18),dp(18),dp(18));
        actionCard.setBackground(round(WATCH,20,0,0));
        LinearLayout.LayoutParams acp=new LinearLayout.LayoutParams(-1,-2);
        acp.setMargins(0,dp(12),0,dp(12));
        actionCard.setLayoutParams(acp);
        actionCard.addView(overlineLight("WHAT TO DO NOW"));
        actionTitle=text("Enter current IOB",27,Color.WHITE,true);
        actionTitle.setPadding(0,dp(5),0,dp(5));
        actionCard.addView(actionTitle);
        actionBody=text("Type the IOB shown in Omnipod. Guidance updates immediately.",14,Color.argb(225,255,255,255),false);
        actionCard.addView(actionBody);
        root.addView(actionCard);

        LinearLayout detail=surface();
        root.addView(detail);
        detail.addView(overline("DETAIL"));

        LinearLayout metricRow=new LinearLayout(this);
        metricRow.setOrientation(LinearLayout.HORIZONTAL);
        metricRow.setPadding(0,dp(10),0,0);
        carbEq=metric(metricRow,"IOB carb-equivalent");
        buffer=metric(metricRow,"Buffer to target");
        detail.addView(metricRow);

        detailLine=text("Waiting for glucose and IOB.",13,MUTED,false);
        detailLine.setPadding(0,dp(14),0,0);
        detail.addView(detailLine);

        TextView foot=text("Experimental personal decision aid only. If you are actually hypo, follow your established hypo treatment plan.",11,Color.rgb(112,122,143),false);
        foot.setPadding(0,dp(16),0,0);
        root.addView(foot);

        return scroll;
    }

    private void addPreset(LinearLayout row,double value){
        Button b=chip(String.format(Locale.UK,"%.1f",value));
        b.setOnClickListener(v->{
            iob.setText(String.format(Locale.UK,"%.1f",value));
            iob.setSelection(iob.getText().length());
        });
        row.addView(b);
    }

    private void stepIob(double amount){
        double current=parse(iob,0);
        current=Math.max(0,Math.round((current+amount)*10.0)/10.0);
        iob.setText(String.format(Locale.UK,"%.1f",current));
        iob.setSelection(iob.getText().length());
    }

    private void refresh(){
        status.setText("Reading local GDH server…");
        executor.execute(()->{
            try{
                URL u=new URL("http://127.0.0.1:17580/sgv.json?count=24");
                HttpURLConnection c=(HttpURLConnection)u.openConnection();
                c.setConnectTimeout(2500);
                c.setReadTimeout(2500);
                c.setUseCaches(false);

                BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb=new StringBuilder();
                String line;
                while((line=br.readLine())!=null) sb.append(line);
                br.close();

                JSONArray arr=new JSONArray(sb.toString());
                List<Reading> rows=new ArrayList<>();
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.getJSONObject(i);
                    rows.add(new Reading(o.optLong("date"),toMmol(o.optDouble("sgv",Double.NaN))));
                }
                if(rows.isEmpty()||Double.isNaN(rows.get(0).g)) throw new Exception();

                Reading latest=rows.get(0);
                Reading prev=rows.size()>1?rows.get(1):null;
                Reading r5=nearest(rows,latest.t,5);
                Reading r15=nearest(rows,latest.t,15);

                double one=Double.NaN;
                if(prev!=null){
                    double mins=Math.max(.5,(latest.t-prev.t)/60000.0);
                    one=(latest.g-prev.g)/mins;
                }

                final double fd1=one;
                final double fd5=r5==null?Double.NaN:latest.g-r5.g;
                final double fd15=r15==null?Double.NaN:latest.g-r15.g;

                runOnUiThread(()->{
                    glucose.setText(String.format(Locale.UK,"%.1f mmol/L",latest.g));
                    glucose.setTag(latest.g);
                    d1.setText(formatDelta(fd1)); d1.setTag(fd1);
                    d5.setText(formatDelta(fd5)); d5.setTag(fd5);
                    d15.setText(formatDelta(fd15)); d15.setTag(fd15);
                    status.setText("Updated "+android.text.format.DateFormat.format("HH:mm",latest.t));
                    assess();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    status.setText("Couldn't read GDH local web service");
                    setAction("Can't read glucose","Open GlucoDataHandler and check that its Local Web Service is enabled.",WARN);
                });
            }
        });
    }

    private void assess(){
        Object bgTag=glucose==null?null:glucose.getTag();
        if(!(bgTag instanceof Double)){
            if(actionTitle!=null) setAction("Waiting for glucose","Low Helper will update as soon as GDH data is available.",WATCH);
            return;
        }

        double bg=(Double)bgTag;
        double ICR=prefs.contains("icr")?prefs.getFloat("icr",Float.NaN):Double.NaN;
        double ISF=prefs.contains("isf")?prefs.getFloat("isf",Float.NaN):Double.NaN;
        double tgt=prefs.contains("target")?prefs.getFloat("target",Float.NaN):Double.NaN;

        if(Double.isNaN(ICR)||Double.isNaN(ISF)||Double.isNaN(tgt)||ICR<=0||ISF<=0){
            carbEq.setText("—");
            buffer.setText("—");
            detailLine.setText("Set ICR, ISF and recovery target in Settings once.");
            setAction("Set up the app once","Open Settings and enter your ICR, ISF and recovery target.",WATCH);
            return;
        }

        buffer.setText(String.format(Locale.UK,"%.1fg",Math.max(0,tgt-bg)*ICR/ISF));

        String raw=iob.getText().toString().trim();
        if(raw.isEmpty()){
            carbEq.setText("—");
            detailLine.setText("Glucose and CGM momentum are ready. IOB is still needed.");
            setAction("Enter current IOB","Use the +/− buttons, a preset, or type the Omnipod value.",ACCENT);
            return;
        }

        double insulin=parse(iob,Double.NaN);
        if(Double.isNaN(insulin)||insulin<0){
            setAction("Check IOB","Enter a valid Omnipod IOB value.",WARN);
            return;
        }

        carbEq.setText(String.format(Locale.UK,"%.1fg",insulin*ICR));

        double a=tag(d1), b=tag(d5), c=tag(d15);
        double rate=(safe(c)/15)*.55+(safe(b)/5)*.35+safe(a)*.10;

        int ts=0;
        String trend="stable";
        if(rate<=-.10 || (!Double.isNaN(c)&&c<=-1)){trend="falling quickly";ts=3;}
        else if(rate<=-.035 || (!Double.isNaN(c)&&c<=-.4)){trend="falling";ts=2;}
        else if(rate>=.06 || (!Double.isNaN(c)&&c>=.6)){trend="rising";ts=-1;}
        else if(rate>.015){trend="drifting up";}

        int bs=bg<4?5:bg<4.5?4:bg<5?3:bg<5.5?2:bg<=6?1:0;
        int is=insulin>=2?3:insulin>=1?2:insulin>=.4?1:0;
        int score=bs+ts+is;

        if(bg<4){
            detailLine.setText("Below 4.0 mmol/L. The calculator deliberately steps back here.");
            setAction("Treat the hypo now","Use your normal hypo treatment plan. Low Helper should not override it.",BAD);
        } else if(ts>=3 && (insulin>=.4 || bg<=5.5)){
            detailLine.setText("CGM is "+trend+" and meaningful downward pressure is present.");
            setAction("Fast carbs likely needed now","Strong downward momentum. Correct the immediate fall first, then reassess.",BAD);
        } else if((ts>=2 && score>=4) || (bg<5.0 && ts>=1)){
            detailLine.setText("CGM is "+trend+". There is enough evidence of downward movement to take seriously.");
            setAction("Consider fast carbs now","Meaningful downward trend. Recheck after the initial response rather than stacking carbs blindly.",WARN);
        } else if(ts<=0 && insulin>=1.0 && bg<=tgt){
            detailLine.setText("CGM is "+trend+", but meaningful IOB remains.");
            setAction("Hold and watch closely","No strong fast-carb signal right now. IOB remains, so watch the next reading.",WATCH);
        } else if(score>=2){
            detailLine.setText("Some downward risk remains, but the data does not support a high-urgency response.");
            setAction("Watch the next reading","Keep monitoring. The current data does not justify a strong immediate response.",WATCH);
        } else {
            detailLine.setText("CGM is "+trend+" with little evidence of strong downward pressure.");
            setAction("No immediate carb action","Current glucose and trend do not show a clear need to intervene. Keep monitoring.",GOOD);
        }
    }

    private void setAction(String title,String body,int color){
        if(actionTitle==null)return;
        actionTitle.setText(title);
        actionBody.setText(body);
        actionCard.setBackground(round(color,20,0,0));
    }

    private static double toMmol(double v){return v>35?v/18.0:v;}

    private static Reading nearest(List<Reading> rows,long latest,int mins){
        long target=latest-mins*60000L,bestDiff=Long.MAX_VALUE;
        Reading best=null;
        for(Reading r:rows){
            long d=Math.abs(r.t-target);
            if(d<bestDiff){bestDiff=d;best=r;}
        }
        long tolerance=Math.max(90000L,(long)(mins*60000L*.4));
        return bestDiff<=tolerance?best:null;
    }

    private double tag(TextView v){
        Object x=v.getTag();
        return x instanceof Double?(Double)x:Double.NaN;
    }

    private double safe(double v){return Double.isNaN(v)?0:v;}

    private String formatDelta(double v){
        return Double.isNaN(v)?"—":String.format(Locale.UK,"%+.2f",v);
    }

    private double parse(EditText e,double f){
        try{return Double.parseDouble(e.getText().toString());}
        catch(Exception x){return f;}
    }

    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout surface(){
        LinearLayout l=column();
        l.setPadding(dp(16),dp(16),dp(16),dp(16));
        l.setBackground(round(SURFACE,20,Color.rgb(35,44,62),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(12));
        l.setLayoutParams(p);
        return l;
    }

    private GradientDrawable round(int fill,int radius,int stroke,int strokeWidth){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if(strokeWidth>0)g.setStroke(dp(strokeWidth),stroke);
        return g;
    }

    private TextView text(String s,int sp,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if(bold)v.setTypeface(null,1);
        v.setLineSpacing(0,1.12f);
        return v;
    }

    private TextView overline(String s){
        TextView v=text(s,11,MUTED,true);
        v.setLetterSpacing(.12f);
        return v;
    }

    private TextView overlineLight(String s){
        TextView v=text(s,11,Color.argb(205,255,255,255),true);
        v.setLetterSpacing(.12f);
        return v;
    }

    private EditText numberInput(String value){
        EditText e=new EditText(this);
        e.setText(value);
        e.setTextColor(TEXT);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setBackground(round(SURFACE_2,14,Color.rgb(48,59,82),1));
        e.setPadding(dp(14),dp(10),dp(14),dp(10));
        return e;
    }

    private Button smallButton(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setPadding(dp(14),0,dp(14),0);
        b.setBackground(round(ACCENT_SOFT,14,0,0));
        b.setMinHeight(0); b.setMinimumHeight(0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(40));
        b.setLayoutParams(p);
        return b;
    }

    private Button iconButton(String s){
        Button b=smallButton(s);
        b.setTextSize(22);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(44),dp(44));
        b.setLayoutParams(p);
        return b;
    }

    private Button stepButton(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setTextSize(26);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setBackground(round(ACCENT_SOFT,14,0,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(58),dp(58));
        b.setLayoutParams(p);
        return b;
    }

    private Button chip(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setBackground(round(SURFACE_2,999,Color.rgb(44,53,74),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);
        p.setMargins(dp(3),0,dp(3),0);
        b.setLayoutParams(p);
        return b;
    }

    private TextView deltaPill(LinearLayout row,String label){
        LinearLayout box=column();
        box.setPadding(dp(10),dp(10),dp(10),dp(10));
        box.setGravity(Gravity.CENTER);
        box.setBackground(round(SURFACE_2,14,0,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(dp(3),0,dp(3),0);
        box.setLayoutParams(p);
        box.addView(text(label,11,MUTED,false));
        TextView v=text("—",17,TEXT,true);
        v.setPadding(0,dp(3),0,0);
        box.addView(v);
        row.addView(box);
        return v;
    }

    private TextView metric(LinearLayout row,String label){
        LinearLayout box=column();
        box.setPadding(dp(12),dp(12),dp(12),dp(12));
        box.setBackground(round(SURFACE_2,14,0,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(dp(3),0,dp(3),0);
        box.setLayoutParams(p);
        box.addView(text(label,11,MUTED,false));
        TextView v=text("—",22,TEXT,true);
        v.setPadding(0,dp(4),0,0);
        box.addView(v);
        row.addView(box);
        return v;
    }

    private int dp(int v){
        return (int)(v*getResources().getDisplayMetrics().density+.5f);
    }
}
