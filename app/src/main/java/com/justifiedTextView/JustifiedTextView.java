package com.justifiedTextView;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.support.annotation.Nullable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;


public class JustifiedTextView extends View {
    Paint                        mTextPaint;
    String                       mText;
    ArrayList<TextBlockDrawable> mTextBlocksDrawable;
    int                          mTextSize;
    ColorStateList               mTextColor;
    int                          mCurTextColor;
    int                          w, h;
    float[] widths;
    float   minSymWidth;
    int     font_descent;
    int     font_interline;
    int     font_line_height;
    int     mLinesCount;

    LineBreaker mLineBreaker;


    public JustifiedTextView(Context context) {
        this(context, null);
    }

    public JustifiedTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public JustifiedTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public JustifiedTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        ColorStateList textColor = null;

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        final Resources.Theme theme = context.getTheme();
        String text = "";
        int textSize = 15;

        TypedArray a = theme.obtainStyledAttributes(attrs, R.styleable.JustifiedTextView, defStyleAttr, defStyleRes);

        int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            switch (attr) {
                case R.styleable.JustifiedTextView_text:
                    text = a.getString(attr);
                    break;
                case R.styleable.JustifiedTextView_textColor:
                    textColor = a.getColorStateList(attr);
                    break;
                case R.styleable.JustifiedTextView_textSize:
                    textSize = a.getDimensionPixelSize(attr, textSize);
                    break;
            }
        }
        a.recycle();

        setTextColor(textColor != null ? textColor : ColorStateList.valueOf(0xFF000000));
        setRawTextSize(textSize);
        setText(text);

        mLineBreaker = new LineBreaker();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (TextBlockDrawable textBlockDrawable : mTextBlocksDrawable) {
            canvas.drawText(mText, textBlockDrawable.start, textBlockDrawable.end, textBlockDrawable.x, textBlockDrawable.y, mTextPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (w != widthSize) {
            w = widthSize;
            mLineBreaker.buildTextBlocks();
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            h = heightSize;
        } else {
            if (mText.isEmpty()) {
                h = font_interline;
            } else {
                h = mLinesCount * font_interline + font_descent;
            }
        }

        setMeasuredDimension(w, h);
    }

    class LineBreaker {
        int x, y;
        int                          posLenStart;
        int                          spacesLen;
        int                          posEOL;
        int                          len;
        ArrayList<TextBlockDrawable> words;
        TextBlockDrawable            word;
        TextBlockDrawable            textBlockDrawable;

        private void buildTextBlocks() {
            init();
            if (len == 0) return;

            initNewLine(0, 0);
            for (int pos = 0; pos < len; pos++) {
                if (mText.charAt(pos) == ' ') {
                    spacesLen += minSymWidth;
                    finishLine(pos);
                    words.add(textBlockDrawable);
                    while (mText.charAt(++pos) == ' ') {
                        spacesLen += minSymWidth;
                    }
                    initNewLine(0, pos);
                }

                if (x + widths[pos] + spacesLen > w) {
                    //scan back for first space
                    posEOL = pos;
                    do {
                        if (mText.charAt(pos) == ' ') {
                            pos++;
                            break;
                        }
                    } while (--pos > posLenStart);

                    //single word does not fit in single line
                    if (pos == posLenStart) {
                        pos = posEOL;

                        newLineResetValues();
                        finishLine(pos);
                        initNewLine(0, pos);

                        posLenStart = pos;

                        x += widths[pos];
                        continue;
                    } else if (pos < 0) {
                        //cant fit even 1 char
                        return;
                    }

                    redistributeSpaces();
                    newLineResetValues();

                    textBlockDrawable.x = x;
                    textBlockDrawable.y = y + font_line_height;
                    posLenStart = pos;
                }
                x += widths[pos];
            }
            textBlockDrawable.end = len;
            mTextBlocksDrawable.add(textBlockDrawable);
            mLinesCount++;
        }

        private void init() {
            y = 0;
            x = 0;
            posLenStart = 0;
            spacesLen = 0;
            mLinesCount = 0;
            words = new ArrayList<>();
            len = mText.length();
            mTextBlocksDrawable.clear();
        }

        private void initNewLine(int yOffset, int pos) {
            textBlockDrawable = new TextBlockDrawable(x, y + font_line_height + yOffset, pos);
        }

        private void finishLine(int pos) {
            textBlockDrawable.end = pos;
            mTextBlocksDrawable.add(textBlockDrawable);
        }

        private void newLineResetValues() {
            spacesLen = 0;
            x = 0;
            y += font_interline;
            mLinesCount++;
        }

        private void redistributeSpaces() {
            int widthTotal = w;

            if (words.size() <= 1) {
                words.clear();
                return;
            }

            for (TextBlockDrawable wrd : words) {
                for (int i = wrd.start; i < wrd.end; i++) {
                    widthTotal -= widths[i];
                }
            }

            int wordsCount = words.size() - 1;
            int spaceLen = widthTotal / wordsCount;
            int spacesMod = widthTotal % wordsCount;
            int spacesShift = 0;
            for (TextBlockDrawable word : words) {
                word.x += spacesShift;
                spacesShift += spaceLen;

                if (spacesMod-- > 0) {
                    spacesShift++;
                }
            }

            words.clear();
        }
    }

    public void setTextColor(ColorStateList colors) {
        mTextColor = colors;

        int color = mTextColor.getColorForState(getDrawableState(), 0);
        if (color != mCurTextColor) {
            mCurTextColor = color;
            mTextPaint.setColor(mCurTextColor);
        }
    }

    private void setRawTextSize(int size) {
        if (size != mTextPaint.getTextSize()) {
            mTextSize = size;
            mTextPaint.setTextSize(size);

            FontMetricsInt fm = mTextPaint.getFontMetricsInt();
            font_descent = fm.descent;
            font_interline = fm.descent - fm.ascent;
            font_line_height = -fm.top;

            float[] widths = new float[1];
            mTextPaint.getTextWidths(" ", widths);
            minSymWidth = widths[0];
        }
    }

    public void setText(String text) {
        mText = text;
        mTextBlocksDrawable = new ArrayList<>();

        widths = new float[mText.length()];
        mTextPaint.getTextWidths(mText, widths);

        if (w == 0) return;

        mLineBreaker.buildTextBlocks();

        invalidate();
    }

    static class TextBlockDrawable {
        int x;
        int y;
        int start;
        int end;

        TextBlockDrawable(int x, int y, int start) {
            this.start = start;
            this.x = x;
            this.y = y;
        }
    }
}