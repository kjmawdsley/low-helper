package uk.kjmawdsley.lowhelper;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
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
    private static final int BG = Color.rgb(11,16,32);
    private static final int CARD = Color.rgb(20,27,45);
    private static final int PANEL = Color.rgb(14,21,37);
    private static final int TEXT = Color.rgb(247,248,251);
    private static final int MUTED = Color.rgb(154,165,186);
    private static final int ACCENT = Color.rgb(127,140,255);
    private static final int GOOD = Color.rgb(45,170,118);
    private static final int WATCH = Color.rgb(68,112,194);
    private static final int WARN = Color.rgb(196,126,37);
    private static final int BAD = Color.rgb(190,66,70);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView glucose, d1, d5, d15, status, actionTitle, actionBody, risk, eq, buffer, fast, slow, why;
    private LinearLayout actionCard;
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
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);

        LinearLayout root=col(0);
        root.setPadding(dp(18),dp(22),dp(18),dp(36));
        scroll.addView(root);

        TextView title=t("Low Helper",30,TEXT,true);
        root.addView(title);
        TextView sub=t("Glucose and trend come straight from GlucoDataHandler. Enter current Omnipod IOB and the guidance updates immediately.",14,MUTED,false);
        sub.setPadding(0,dp(4),0,dp(18));
        root.addView(sub);

        LinearLayout glucoseCard=card();
        root.addView(glucoseCard);
        glucoseCard.addView(label("LIVE GLUCOSE"));
        glucose=t("—",38,TEXT,true);
        glucoseCard.addView(glucose);
        status=t("Connecting to GDH…",12,MUTED,false);
        status.setPadding(0,dp(3),0,dp(12));
        glucoseCard.addView(status);

        LinearLayout deltas=new LinearLayout(this);
        deltas.setOrientation(LinearLayout.HORIZONTAL);
        d1=deltaBox(deltas,"1 min");
        d5=deltaBox(deltas,"5 min");
        d15=deltaBox(deltas,"15 min");
        glucoseCard.addView(deltas);

        Button refresh=button("Refresh glucose", false);
        refresh.setOnClickListener(v->refresh());
        glucoseCard.addView(refresh);

        LinearLayout iobCard=card();
        root.addView(iobCard);
        iobCard.addView(label("OMNIPOD IOB"));
        TextView iobHelp=t("Enter the current IOB shown in Omnipod",13,MUTED,false);
        iobHelp.setPadding(0,dp(2),0,dp(9));
        iobCard.addView(iobHelp);
        iob=number("");
        iob.setTextSize(28);
        iob.setHint("e.g. 1.2");
        iob.setHintTextColor(Color.rgb(90,101,124));
        iobCard.addView(iob);
        iob.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ assess(); }
            public void afterTextChanged(Editable e){}
        });

        actionCard=col(16);
        actionCard.setPadding(dp(18),dp(18),dp(18),dp(18));
        actionCard.setBackground(round(PANEL,18,Color.TRANSPARENT,0));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);
        ap.setMargins(0,0,0,dp(14));
        actionCard.setLayoutParams(ap);
        root.addView(actionCard);

        TextView actionLabel=t("WHAT TO DO NOW",12,Color.argb(210,255,255,255),true);
        actionCard.addView(actionLabel);
        actionTitle=t("Waiting for glucose",26,Color.WHITE,true);
        actionTitle.setPadding(0,dp(5),0,dp(5));
        actionCard.addView(actionTitle);
        actionBody=t("Low Helper will update as soon as GDH data is available.",14,Color.argb(225,255,255,255),false);
        actionCard.addView(actionBody);

        LinearLayout detail=card();
        root.addView(detail);
        detail.addView(label("DETAIL"));
        risk=t("—",20,TEXT,true);
        detail.addView(risk);

        LinearLayout metrics=new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0,dp(13),0,0);
        eq=metric(metrics,"IOB carb-equivalent");
        buffer=metric(metrics,"Buffer to target");
        detail.addView(metrics);

        detail.addView(labelPad("Fast-carb emphasis",16));
        fast=t("—",14,MUTED,false);
        detail.addView(fast);

        detail.addView(labelPad("Slower-carb emphasis",12));
        slow=t("—",14,MUTED,false);
        detail.addView(slow);

        why=t("",13,MUTED,false);
        why.setPadding(0,dp(14),0,0);
        detail.addView(why);

        LinearLayout settings=card();
        root.addView(settings);
        settings.addView(t("Settings",17,TEXT,true));
        TextView settingsNote=t("Saved only on this phone.",12,MUTED,false);
        settingsNote.setPadding(0,dp(3),0,0);
        settings.addView(settingsNote);

        settings.addView(labelPad("ICR — grams per unit",12));
        icr=number(prefs.contains("icr")?String.valueOf(prefs.getFloat("icr",0f)):"");
        settings.addView(icr);

        settings.addView(labelPad("ISF — mmol/L per unit",12));
        isf=number(prefs.contains("isf")?String.valueOf(prefs.getFloat("isf",0f)):"");
        settings.addView(isf);

        settings.addView(labelPad("Recovery target — mmol/L",12));
        target=number(prefs.contains("target")?String.valueOf(prefs.getFloat("target",0f)):"");
        settings.addView(target);

        Button save=button("Save settings", true);
        save.setOnClickListener(v->{
            if(saveSettings()){
                assess();
                Toast.makeText(this,"Settings saved",Toast.LENGTH_SHORT).show();
            }
        });
        settings.addView(save);

        TextView foot=t("Experimental personal decision aid only. If you are actually hypo, use your established hypo treatment plan rather than this calculation.",12,MUTED,false);
        foot.setPadding(0,dp(2),0,0);
        root.addView(foot);
        return scroll;
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
                if(rows.isEmpty()||Double.isNaN(rows.get(0).g)) throw new Exception("No usable readings");

                Reading latest=rows.get(0);
                Reading prev=rows.size()>1?rows.get(1):null;
                Reading r5=nearest(rows,latest.t,5);
                Reading r15=nearest(rows,latest.t,15);

                double one=Double.NaN;
                if(prev!=null){
                    double mins=Math.max(0.5,(latest.t-prev.t)/60000.0);
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
                    status.setText("GDH connected · "+android.text.format.DateFormat.format("HH:mm",latest.t));
                    assess();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    status.setText("Couldn't read GDH. Check that Local Web Service is enabled.");
                    setAction("Can't read glucose","Open GlucoDataHandler and make sure its Local Web Service is running.",WARN);
                });
            }
        });
    }

    private void assess(){
        Object bgTag=glucose.getTag();
        if(!(bgTag instanceof Double)){
            risk.setText("Waiting for GDH");
            setAction("Waiting for glucose","Low Helper will update as soon as GDH data is available.",WATCH);
            return;
        }

        double bg=(Double)bgTag;
        double ICR=parse(icr,Double.NaN);
        double ISF=parse(isf,Double.NaN);
        double tgt=parse(target,Double.NaN);

        if(Double.isNaN(ICR)||Double.isNaN(ISF)||Double.isNaN(tgt)||ICR<=0||ISF<=0){
            risk.setText("Enter settings once");
            eq.setText("—"); buffer.setText("—");
            fast.setText("Set ICR, ISF and recovery target below.");
            slow.setText("These values stay only on this phone.");
            why.setText("");
            setAction("Set up the app once","Enter ICR, ISF and your recovery target in Settings, then save.",WATCH);
            return;
        }

        String iobText=iob.getText().toString().trim();
        if(iobText.isEmpty()){
            risk.setText("Waiting for IOB");
            eq.setText("—");
            buffer.setText(String.format(Locale.UK,"%.1fg",Math.max(0,tgt-bg)*ICR/ISF));
            fast.setText("Trend is ready; IOB is still needed.");
            slow.setText("Enter the value currently shown in Omnipod.");
            why.setText("Glucose and deltas are already loaded from GDH.");
            setAction("Enter current IOB","Type the IOB shown in Omnipod. Guidance updates immediately.",ACCENT);
            return;
        }

        double insulin=parse(iob,Double.NaN);
        if(Double.isNaN(insulin)||insulin<0){
            setAction("Check IOB","Enter a valid Omnipod IOB value.",WARN);
            return;
        }

        double a=tag(d1), b=tag(d5), c=tag(d15);
        double rate=(safe(c)/15)*.55+(safe(b)/5)*.35+safe(a)*.10;

        int ts=0;
        String trend="Stable";
        if(rate<=-.10 || (!Double.isNaN(c)&&c<=-1)){trend="Falling quickly";ts=3;}
        else if(rate<=-.035 || (!Double.isNaN(c)&&c<=-.4)){trend="Falling";ts=2;}
        else if(rate>=.06 || (!Double.isNaN(c)&&c>=.6)){trend="Rising";ts=-1;}
        else if(rate>.015){trend="Drifting up";}

        int bs=bg<4?5:bg<4.5?4:bg<5?3:bg<5.5?2:bg<=6?1:0;
        int is=insulin>=2?3:insulin>=1?2:insulin>=.4?1:0;
        int score=bs+ts+is;

        String r=bg<4?"Hypo":score>=7?"High downward pressure":score>=4?"Meaningful downward pressure":score>=2?"Watch closely":"Low immediate risk";
        risk.setText(r+" · "+trend);
        eq.setText(String.format(Locale.UK,"%.1fg",insulin*ICR));
        buffer.setText(String.format(Locale.UK,"%.1fg",Math.max(0,tgt-bg)*ICR/ISF));

        if(bg<4){
            fast.setText("Do not use this model to determine hypo treatment.");
            slow.setText("Reassess only after the immediate low is corrected.");
            why.setText("The calculator deliberately steps back once glucose is below 4.0.");
            setAction("Treat the hypo now","Use your normal hypo treatment plan. Low Helper should not override it.",BAD);
            return;
        }

        if(ts>=3 && (insulin>=0.4 || bg<=5.5)){
            fast.setText("High — the immediate downward trend is the dominant signal.");
            slow.setText(insulin>=.7?"Ongoing insulin pressure remains after the immediate correction.":"Relatively little IOB remains.");
            why.setText("The CGM shows sustained downward momentum"+(insulin>=.4?" and there is insulin on board.":"."));
            setAction("Fast carbs likely needed now","Strong downward momentum. Correct the immediate fall first, then reassess.",BAD);
        } else if((ts>=2 && score>=4) || (bg<5.0 && ts>=1)){
            fast.setText("Moderate — the longer CGM windows show a meaningful fall.");
            slow.setText(insulin>=.7?"Moderate ongoing insulin pressure remains.":"Lower ongoing insulin pressure.");
            why.setText("The downward trend is real enough to take seriously, but not as aggressive as the highest-risk state.");
            setAction("Consider fast carbs now","Meaningful downward trend. Recheck after the immediate response rather than stacking carbs blindly.",WARN);
        } else if(ts<=0 && insulin>=1.0 && bg<=tgt){
            fast.setText("Low — there is no strong immediate drop signal.");
            slow.setText("Moderate — meaningful IOB remains, so later downward pressure is still possible.");
            why.setText("Your glucose is not falling hard right now, but the IOB means this is not completely 'done'.");
            setAction("Hold and watch closely","No strong fast-carb signal right now. IOB remains, so keep an eye on the next reading.",WATCH);
        } else if(score>=2){
            fast.setText(ts>=2?"Moderate.":"Low.");
            slow.setText(insulin>=.7?"Moderate ongoing insulin pressure.":"Low ongoing insulin pressure.");
            why.setText("There is some downward risk, but the data does not support a high-urgency response.");
            setAction("Watch the next reading","There is some risk, but not enough evidence for a strong immediate response.",WATCH);
        } else {
            fast.setText("Low — no strong short-term drop signal.");
            slow.setText(insulin>=.7?"Some IOB remains, but the current trend is reassuring.":"Low — relatively little IOB remains.");
            why.setText("Little evidence of strong downward pressure right now.");
            setAction("No immediate carb action","Current glucose and trend do not show a clear need to intervene. Keep monitoring.",GOOD);
        }
    }

    private void setAction(String title,String body,int color){
        actionTitle.setText(title);
        actionBody.setText(body);
        actionCard.setBackground(round(color,18,Color.TRANSPARENT,0));
    }

    private boolean saveSettings(){
        double a=parse(icr,Double.NaN), b=parse(isf,Double.NaN), c=parse(target,Double.NaN);
        if(Double.isNaN(a)||Double.isNaN(b)||Double.isNaN(c)||a<=0||b<=0){
            Toast.makeText(this,"Enter valid ICR, ISF and target",Toast.LENGTH_SHORT).show();
            return false;
        }
        prefs.edit().putFloat("icr",(float)a).putFloat("isf",(float)b).putFloat("target",(float)c).apply();
        return true;
    }

    private static double toMmol(double v){ return v>35?v/18.0:v; }

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

    private double safe(double v){ return Double.isNaN(v)?0:v; }

    private String formatDelta(double v){
        return Double.isNaN(v)?"—":String.format(Locale.UK,"%+.2f",v);
    }

    private double parse(EditText e,double f){
        try{return Double.parseDouble(e.getText().toString());}
        catch(Exception x){return f;}
    }

    private LinearLayout col(int pad){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(pad),dp(pad),dp(pad),dp(pad));
        return l;
    }

    private LinearLayout card(){
        LinearLayout l=col(16);
        l.setBackground(round(CARD,18,Color.rgb(42,53,77),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(14));
        l.setLayoutParams(p);
        return l;
    }

    private GradientDrawable round(int fill,int radius,int stroke,int strokeWidth){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if(strokeWidth>0) g.setStroke(dp(strokeWidth),stroke);
        return g;
    }

    private TextView t(String s,int sp,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if(bold)v.setTypeface(null,1);
        v.setLineSpacing(0,1.15f);
        return v;
    }

    private TextView label(String s){
        TextView v=t(s,12,MUTED,true);
        v.setLetterSpacing(.08f);
        return v;
    }

    private TextView labelPad(String s,int top){
        TextView v=label(s);
        v.setPadding(0,dp(top),0,dp(6));
        return v;
    }

    private EditText number(String value){
        EditText e=new EditText(this);
        e.setText(value);
        e.setTextColor(TEXT);
        e.setTextSize(20);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setBackground(round(PANEL,12,Color.rgb(52,64,90),1));
        e.setPadding(dp(14),dp(12),dp(14),dp(12));
        return e;
    }

    private Button button(String s, boolean primary){
        Button b=new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setBackground(round(primary?ACCENT:Color.rgb(33,43,68),12,Color.TRANSPARENT,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));
        p.setMargins(0,dp(12),0,0);
        b.setLayoutParams(p);
        return b;
    }

    private TextView deltaBox(LinearLayout row,String label){
        LinearLayout box=col(8);
        box.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(dp(3),0,dp(3),0);
        box.setLayoutParams(p);
        box.setBackground(round(PANEL,12,Color.TRANSPARENT,0));
        box.addView(t(label,12,MUTED,false));
        TextView v=t("—",18,TEXT,true);
        v.setPadding(0,dp(4),0,0);
        box.addView(v);
        row.addView(box);
        return v;
    }

    private TextView metric(LinearLayout row,String label){
        LinearLayout box=col(10);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(dp(3),0,dp(3),0);
        box.setLayoutParams(p);
        box.setBackground(round(PANEL,12,Color.TRANSPARENT,0));
        box.addView(t(label,11,MUTED,false));
        TextView v=t("—",22,TEXT,true);
        box.addView(v);
        row.addView(box);
        return v;
    }

    private int dp(int v){
        return (int)(v*getResources().getDisplayMetrics().density+.5f);
    }
}
