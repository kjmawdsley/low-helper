package uk.kjmawdsley.lowhelper;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

public class SettingsActivity extends Activity {
    private static final int BG=Color.rgb(9,13,24), SURFACE=Color.rgb(18,24,39), SURFACE_2=Color.rgb(13,19,33);
    private static final int TEXT=Color.rgb(248,249,252), MUTED=Color.rgb(147,158,179), ACCENT=Color.rgb(134,143,255);
    private EditText icr,isf,target;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        setContentView(buildUi());
    }

    private ScrollView buildUi(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);

        LinearLayout root=col();
        root.setPadding(dp(18),dp(18),dp(18),dp(34));
        scroll.addView(root);

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button back=button("‹",false);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(44),dp(44));
        back.setLayoutParams(bp);
        back.setTextSize(28);
        back.setOnClickListener(v->finish());
        top.addView(back);

        TextView title=text("Settings",28,TEXT,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);
        tp.setMargins(dp(10),0,0,0);
        title.setLayoutParams(tp);
        top.addView(title);
        root.addView(top);

        TextView sub=text("Saved only on this phone. Low Helper uses these values for its estimates.",14,MUTED,false);
        sub.setPadding(0,dp(8),0,dp(18));
        root.addView(sub);

        LinearLayout card=surface();
        root.addView(card);

        card.addView(label("ICR — grams per unit"));
        icr=number(prefs.contains("icr")?String.valueOf(prefs.getFloat("icr",0f)):"");
        card.addView(icr);

        card.addView(labelPad("ISF — mmol/L per unit",16));
        isf=number(prefs.contains("isf")?String.valueOf(prefs.getFloat("isf",0f)):"");
        card.addView(isf);

        card.addView(labelPad("Recovery target — mmol/L",16));
        target=number(prefs.contains("target")?String.valueOf(prefs.getFloat("target",0f)):"");
        card.addView(target);

        Button save=button("Save settings",true);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(52));
        sp.setMargins(0,dp(18),0,0);
        save.setLayoutParams(sp);
        save.setOnClickListener(v->save());
        card.addView(save);

        TextView note=text("These settings are not uploaded anywhere.",12,MUTED,false);
        note.setPadding(0,dp(14),0,0);
        card.addView(note);

        return scroll;
    }

    private void save(){
        double a=parse(icr),b=parse(isf),c=parse(target);
        if(Double.isNaN(a)||Double.isNaN(b)||Double.isNaN(c)||a<=0||b<=0){
            Toast.makeText(this,"Enter valid values",Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putFloat("icr",(float)a).putFloat("isf",(float)b).putFloat("target",(float)c).apply();
        Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show();
        finish();
    }

    private double parse(EditText e){
        try{return Double.parseDouble(e.getText().toString());}
        catch(Exception ex){return Double.NaN;}
    }

    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}

    private LinearLayout surface(){
        LinearLayout l=col();
        l.setPadding(dp(16),dp(16),dp(16),dp(16));
        l.setBackground(round(SURFACE,20,Color.rgb(35,44,62),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        l.setLayoutParams(p);
        return l;
    }

    private TextView text(String s,int sp,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(null,1);return v;
    }

    private TextView label(String s){return text(s,12,MUTED,true);}
    private TextView labelPad(String s,int top){TextView v=label(s);v.setPadding(0,dp(top),0,dp(6));return v;}

    private EditText number(String value){
        EditText e=new EditText(this);
        e.setText(value);e.setTextColor(TEXT);e.setTextSize(20);e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setBackground(round(SURFACE_2,14,Color.rgb(48,59,82),1));
        e.setPadding(dp(14),dp(12),dp(14),dp(12));
        return e;
    }

    private Button button(String s,boolean primary){
        Button b=new Button(this);
        b.setText(s);b.setTextColor(TEXT);b.setTextSize(15);b.setAllCaps(false);
        b.setBackground(round(primary?ACCENT:Color.rgb(36,42,73),14,0,0));
        return b;
    }

    private GradientDrawable round(int fill,int radius,int stroke,int strokeWidth){
        GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));
        if(strokeWidth>0)g.setStroke(dp(strokeWidth),stroke);
        return g;
    }

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
}
