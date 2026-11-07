package com.pocketdex.launcher.eco;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.pocketdex.launcher.Ui;

public class CalculatorActivity extends Activity {

    private static final int BG = 0xFF0B0D12;
    private static final int CARD = 0xFF171A24;
    private static final int PILL = 0x33232838;
    private static final int TEXT = 0xFFF4F6FB;
    private static final int DIM = 0xFF9AA3B5;
    private static final int ACCENT = 0xFF4C8DFF;

    private static final String[][] ROWS = {
            {"C", "(", ")", "⌫"},
            {"7", "8", "9", "÷"},
            {"4", "5", "6", "×"},
            {"1", "2", "3", "−"},
            {"0", ".", "=", "+"},
    };

    private TextView display;
    private TextView preview;
    private StringBuilder expr = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(18), dp(16), dp(18));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackground(Ui.roundedStroke(this, CARD, 14, 0xFF2A3040, 1));
        top.setPadding(dp(12), dp(10), dp(12), dp(10));

        display = new TextView(this);
        display.setTextColor(TEXT);
        display.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        display.setTypeface(Typeface.DEFAULT_BOLD);
        display.setGravity(Gravity.END);
        display.setPadding(0, dp(2), 0, dp(2));
        top.addView(display);

        preview = new TextView(this);
        preview.setTextColor(DIM);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        preview.setGravity(Gravity.END);
        preview.setPadding(0, dp(2), 0, dp(2));
        top.addView(preview);

        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        for (String[] rowLabels : ROWS) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
            rowParams.topMargin = dp(8);
            for (String label : rowLabels) {
                row.addView(calcButton(label), new LinearLayout.LayoutParams(
                        0, dp(56), 1f));
            }
            root.addView(row, rowParams);
        }

        root.addView(new TextView(this));

        return root;
    }

    private TextView calcButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.ripple(this,
                Ui.rounded(this, PILL, 12), 0x44FFFFFF));
        button.setOnClickListener(v -> press(label));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                button.getLayoutParams();
        params.rightMargin = dp(8);
        return button;
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private void press(String label) {
        switch (label) {
            case "C":
                expr.setLength(0);
                preview.setText("");
                break;
            case "⌫":
                if (expr.length() > 0) {
                    expr.setLength(expr.length() - 1);
                }
                break;
            case "=":
                calculate();
                break;
            case "÷":
                expr.append('/');
                break;
            case "×":
                expr.append('*');
                break;
            case "−":
                expr.append('-');
                break;
            default:
                expr.append(label);
                break;
        }
        display.setText(expr.toString());
        refreshPreview();
    }

    private void calculate() {
        String source = expr.toString();
        try {
            double value = evaluate(source);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                preview.setText("Math error");
                return;
            }
            expr.setLength(0);
            expr.append(format(value));
            display.setText(expr.toString());
            preview.setText("");
        } catch (Exception e) {
            preview.setText("Error");
        }
    }

    private void refreshPreview() {
        String source = expr.toString();
        if (source.isEmpty()) {
            preview.setText("");
            return;
        }
        try {
            double value = evaluate(source);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return;
            }
            preview.setText("= " + format(value));
        } catch (Exception ignored) {
        }
    }

    private static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15d) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static double evaluate(String source) throws Exception {
        return new Parser(source).parseExpression();
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        private double parseExpression() throws Exception {
            double value = parseTerm();
            while (true) {
                char op = peek();
                if (op == '+') {
                    pos++;
                    value += parseTerm();
                } else if (op == '-') {
                    pos++;
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() throws Exception {
            double value = parsePower();
            while (true) {
                char op = peek();
                if (op == '*') {
                    pos++;
                    value *= parsePower();
                } else if (op == '/') {
                    pos++;
                    double divisor = parsePower();
                    if (divisor == 0d) {
                        throw new ArithmeticException("divide by zero");
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        private double parsePower() throws Exception {
            double base = parseUnary();
            if (peek() == '^') {
                pos++;
                return Math.pow(base, parsePower());
            }
            return base;
        }

        private double parseUnary() throws Exception {
            if (peek() == '-') {
                pos++;
                return -parseUnary();
            }
            return parseAtom();
        }

        private double parseAtom() throws Exception {
            skip();
            if (pos < src.length() && src.charAt(pos) == '(') {
                pos++;
                double value = parseExpression();
                skip();
                if (pos >= src.length() || src.charAt(pos) != ')') {
                    throw new Exception("expected )");
                }
                pos++;
                return value;
            }
            int start = pos;
            while (pos < src.length()
                    && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new Exception("number expected");
            }
            return Double.parseDouble(src.substring(start, pos));
        }

        private char peek() {
            skip();
            char op = ' ';
            if (pos < src.length()) {
                op = src.charAt(pos);
                if ("+-*/".indexOf(op) < 0) {
                    op = ' ';
                }
            }
            return op;
        }

        private void skip() {
            while (pos < src.length()
                    && (src.charAt(pos) == ' ' || src.charAt(pos) == '\t')) {
                pos++;
            }
        }
    }
}
