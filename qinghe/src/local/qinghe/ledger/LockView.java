package local.qinghe.ledger;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 青禾自己的锁屏：暖白底 + 圆角卡片 + 圆点 + 自家数字键盘。
 * 不用系统对话框、不弹系统键盘，外观与网页界面一致。
 */
public class LockView extends FrameLayout {
 public interface Listener { void onSubmit(String pin); void onCancel(); }

 private static final int BG=0xFFF6F7F2, CARD=0xFFFFFFFF, INK=0xFF203A2C,
   MUTED=0xFF78867B, GREEN=0xFF245E48, KEY=0xFFF1F4EF, LINE=0xFFE1E7DD;

 private final TextView titleV, msgV, doneV;
 private final LinearLayout dots;
 private final StringBuilder buf = new StringBuilder();
 private int maxLen = 8, minLen = 4;
 private boolean exact = false;
 private boolean busy = false;
 private Listener listener;

 public LockView(Context c, String title, String msg, boolean cancel) {
  super(c);
  setBackgroundColor(BG);
  setClickable(true);   // 吃掉所有没落在按钮上的触摸，防止穿透到下面的网页
  setFocusable(true);

  LinearLayout card = new LinearLayout(c);
  card.setOrientation(LinearLayout.VERTICAL);
  card.setPadding(dp(22), dp(26), dp(22), dp(16));
  GradientDrawable bg = new GradientDrawable();
  bg.setColor(CARD);
  bg.setCornerRadius(dp(26));
  card.setBackground(bg);

  titleV = text(title, 19, INK, true);
  card.addView(titleV, wide());

  msgV = text(msg, 13, MUTED, false);
  msgV.setPadding(0, dp(8), 0, 0);
  card.addView(msgV, wide());

  dots = new LinearLayout(c);
  dots.setOrientation(LinearLayout.HORIZONTAL);
  dots.setGravity(Gravity.CENTER);
  dots.setPadding(0, dp(24), 0, dp(16));
  card.addView(dots, wide());

  card.addView(keypad(), wide());

  doneV = text("确定", 16, 0xFFFFFFFF, true);
  doneV.setGravity(Gravity.CENTER);
  doneV.setPadding(0, dp(16), 0, dp(16));
  GradientDrawable dbg = new GradientDrawable();
  dbg.setColor(GREEN);
  dbg.setCornerRadius(dp(16));
  doneV.setBackground(dbg);
  doneV.setOnClickListener(v -> submit());
  LinearLayout.LayoutParams dlp = wide();
  dlp.topMargin = dp(16);
  card.addView(doneV, dlp);

  if (cancel) {
   TextView cancelV = text("退出", 13, MUTED, false);
   cancelV.setGravity(Gravity.CENTER);
   cancelV.setPadding(0, dp(16), 0, dp(4));
   cancelV.setOnClickListener(v -> { if (listener != null) listener.onCancel(); });
   card.addView(cancelV, wide());
  }

  FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
  lp.gravity = Gravity.CENTER;
  lp.setMargins(dp(18), dp(18), dp(18), dp(18));
  addView(card, lp);

  rebuildDots();
 }

 public void setListener(Listener l) { listener = l; }
 public void setTitle(String t) { titleV.setText(t); }
 public void setMessage(String m) { msgV.setText(m); }
 public void setMaxLen(int n) { maxLen = n; rebuildDots(); }
 /** 固定位数（4 或 6）：输满自动提交，并隐藏“确定” */
 public void setExact(int n) { minLen = n; maxLen = n; exact = true; doneV.setVisibility(GONE); rebuildDots(); }
 public void clear() { buf.setLength(0); refresh(); }
 /** 校验中：锁住键盘，避免重复提交，也让用户知道“在算” */
 public void setBusy(boolean b) { busy = b; if (b) doneV.setEnabled(false); else refresh(); }

 private void rebuildDots() {
  dots.removeAllViews();
  for (int i = 0; i < maxLen; i++) {
   TextView d = new TextView(getContext());
   d.setText("●");
   d.setTextSize(14);
   LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
     ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
   lp.setMargins(dp(5), 0, dp(5), 0);
   d.setLayoutParams(lp);
   dots.addView(d);
  }
  refresh();
 }

 private void refresh() {
  for (int i = 0; i < dots.getChildCount(); i++) {
   ((TextView) dots.getChildAt(i)).setTextColor(i < buf.length() ? GREEN : LINE);
  }
  boolean ok = buf.length() >= minLen;
  doneV.setEnabled(ok);
  doneV.setAlpha(ok ? 1f : .45f);
 }

 private void press(String d) {
  if (busy || buf.length() >= maxLen) return;
  buf.append(d);
  refresh();
  if (exact && buf.length() >= maxLen) submit();
 }

 private void back() {
  if (buf.length() > 0) { buf.deleteCharAt(buf.length() - 1); refresh(); }
 }

 private void submit() {
  if (busy || buf.length() < minLen || listener == null) return;
  listener.onSubmit(buf.toString());
 }

 private View keypad() {
  LinearLayout pad = new LinearLayout(getContext());
  pad.setOrientation(LinearLayout.VERTICAL);
  String[][] rows = { {"1","2","3"}, {"4","5","6"}, {"7","8","9"}, {null,"0","⌫"} };
  for (String[] row : rows) {
   LinearLayout r = new LinearLayout(getContext());
   r.setOrientation(LinearLayout.HORIZONTAL);
   for (String k : row) {
    if (k == null) {
     View spacer = new View(getContext());
     LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(58), 1f);
     sp.setMargins(dp(5), dp(5), dp(5), dp(5));
     spacer.setLayoutParams(sp);
     r.addView(spacer);
    } else {
     TextView v = text(k, 22, INK, false);
     v.setGravity(Gravity.CENTER);
     GradientDrawable kb = new GradientDrawable();
     kb.setColor(KEY);
     kb.setCornerRadius(dp(16));
     v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33245E48), kb, null));
     v.setOnClickListener(x -> { if ("⌫".equals(k)) back(); else press(k); });
     LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1f);
     lp.setMargins(dp(5), dp(5), dp(5), dp(5));
     v.setLayoutParams(lp);
     r.addView(v);
    }
   }
   pad.addView(r, wide());
  }
  return pad;
 }

 private TextView text(String s, int sp, int color, boolean bold) {
  TextView v = new TextView(getContext());
  v.setText(s);
  v.setTextSize(sp);
  v.setTextColor(color);
  if (bold) v.setTypeface(v.getTypeface(), Typeface.BOLD);
  return v;
 }

 private LinearLayout.LayoutParams wide() {
  return new LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
 }

 private int dp(float v) {
  return Math.round(v * getResources().getDisplayMetrics().density);
 }
}

/** 一个和青禾同风格的选择列表（两三个选项时用，替代系统 AlertDialog 列表） */
class ChoiceView extends FrameLayout {
 interface L { void onPick(int i); void onCancel(); }

 ChoiceView(Context c, String title, String[] items, final L l) {
  super(c);
  setBackgroundColor(0xFFF6F7F2);
  setClickable(true);   // 同上：不许穿透
  setFocusable(true);

  LinearLayout card = new LinearLayout(c);
  card.setOrientation(LinearLayout.VERTICAL);
  card.setPadding(dp(12), dp(18), dp(12), dp(8));
  GradientDrawable bg = new GradientDrawable();
  bg.setColor(0xFFFFFFFF);
  bg.setCornerRadius(dp(24));
  card.setBackground(bg);

  TextView t = new TextView(c);
  t.setText(title);
  t.setTextSize(14);
  t.setTextColor(0xFF78867B);
  t.setPadding(dp(12), 0, dp(12), dp(8));
  card.addView(t);

  for (int i = 0; i < items.length; i++) {
   final int idx = i;
   TextView row = new TextView(c);
   row.setText(items[i]);
   row.setTextSize(17);
   row.setTextColor(0xFF203A2C);
   row.setPadding(dp(14), dp(17), dp(14), dp(17));
   GradientDrawable rb = new GradientDrawable();
   rb.setColor(0x00000000);
   rb.setCornerRadius(dp(14));
   row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33245E48), rb, null));
   row.setOnClickListener(v -> l.onPick(idx));
   card.addView(row);
  }

  TextView cancel = new TextView(c);
  cancel.setText("取消");
  cancel.setTextSize(14);
  cancel.setTextColor(0xFF78867B);
  cancel.setGravity(Gravity.CENTER);
  cancel.setPadding(0, dp(16), 0, dp(8));
  cancel.setOnClickListener(v -> l.onCancel());
  card.addView(cancel);

  FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
  lp.gravity = Gravity.CENTER;
  lp.setMargins(dp(24), dp(24), dp(24), dp(24));
  addView(card, lp);
 }

 private int dp(float v) {
  return Math.round(v * getResources().getDisplayMetrics().density);
 }
}
