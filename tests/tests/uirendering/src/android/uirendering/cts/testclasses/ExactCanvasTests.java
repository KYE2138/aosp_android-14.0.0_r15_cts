/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.uirendering.cts.testclasses;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.NinePatchDrawable;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapcomparers.BitmapComparer;
import android.uirendering.cts.bitmapcomparers.ExactComparer;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.bitmapverifiers.PerPixelBitmapVerifier;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.util.CompareUtils;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ExactCanvasTests extends ActivityTestBase {
    private final BitmapComparer mExactComparer = new ExactComparer();

    @Test
    public void testBlueRect() {
        final Rect rect = new Rect(10, 10, 80, 80);
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint p = new Paint();
                    p.setAntiAlias(false);
                    p.setColor(Color.BLUE);
                    canvas.drawRect(rect, p);
                })
                .runWithVerifier(new RectVerifier(Color.WHITE, Color.BLUE, rect));
    }

    @Test
    public void testPoints() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint p = new Paint();
                    p.setAntiAlias(false);
                    p.setStrokeWidth(1f);
                    p.setColor(Color.BLACK);
                    canvas.translate(0.5f, 0.5f);
                    for (int i = 0; i < 10; i++) {
                        canvas.drawPoint(i * 10, i * 10, p);
                    }
                })
                .runWithComparer(mExactComparer);
    }

    @Test
    public void testBlackRectWithStroke() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint p = new Paint();
                    p.setColor(Color.RED);
                    canvas.drawRect(0, 0, ActivityTestBase.TEST_WIDTH,
                            ActivityTestBase.TEST_HEIGHT, p);
                    p.setColor(Color.BLACK);
                    p.setStrokeWidth(5);
                    canvas.drawRect(10, 10, 80, 80, p);
                })
                .runWithComparer(mExactComparer);
    }

    @Test
    public void testBlackLineOnGreenBack() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.drawColor(Color.GREEN);
                    Paint p = new Paint();
                    p.setColor(Color.BLACK);
                    p.setStrokeWidth(10);
                    canvas.drawLine(0, 0, 50, 0, p);
                })
                .runWithComparer(mExactComparer);
    }

    @Test
    public void testDrawRedRectOnBlueBack() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.drawColor(Color.BLUE);
                    Paint p = new Paint();
                    p.setColor(Color.RED);
                    canvas.drawRect(10, 10, 40, 40, p);
                })
                .runWithComparer(mExactComparer);
    }

    @Test
    public void testDrawLine() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint p = new Paint();
                    canvas.drawColor(Color.WHITE);
                    p.setColor(Color.BLACK);
                    p.setAntiAlias(false);
                    // ensure the lines do not hit pixel edges
                    canvas.translate(0.05f, 0.05f);
                    float[] pts = {
                            0, 0, 80, 80, 80, 0, 0, 80, 40, 50, 60, 50
                    };
                    canvas.drawLines(pts, p);
                })
                .runWithComparer(new MSSIMComparer(0.99));
    }

    @Test
    public void testDrawWhiteScreen() {
        createTest()
                .addCanvasClient((canvas, width, height) -> canvas.drawColor(Color.WHITE))
                .runWithComparer(mExactComparer);
    }

    @Test
    public void testBasicText() {
        final String testString = "THIS IS A TEST";
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint p = new Paint();
                    canvas.drawColor(Color.BLACK);
                    p.setColor(Color.WHITE);
                    p.setStrokeWidth(5);
                    canvas.drawText(testString, 30, 50, p);
                })
                .runWithComparer(new MSSIMComparer(0.99));
    }

    private void drawTestTextOnPath(Canvas canvas) {
        final String testString = "THIS IS A TEST ON A CIRCLE PATH";
        Path path = new Path();
        path.addCircle(45, 45, 30, Path.Direction.CW);
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        p.setAntiAlias(true);

        File file = TypefaceTestUtil.getFirstFont(testString, p);
        if (!file.getName().startsWith("Roboto")) {
            p.setTypeface(TypefaceTestUtil.getRobotoTypeface(400, false));
        }

        canvas.drawTextOnPath(testString, path, 0f, 0f, p);
    }

    @Test
    public void testTextOnPath() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    drawTestTextOnPath(canvas);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                    // HWUI's texts are blurry, so we lower the threshold.
                    // Note that 0.7 will fail the test.
                    R.drawable.text_on_path, new MSSIMComparer(0.6)));
    }

    @Test
    public void testTextOnPathUsingPicture() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Picture picture = new Picture();
                    Canvas pictureCanvas = picture.beginRecording(90, 90);
                    drawTestTextOnPath(pictureCanvas);
                    picture.endRecording();
                    picture.draw(canvas);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                    // HWUI's texts are blurry, so we lower the threshold.
                    // Note that 0.7 will fail the test.
                    R.drawable.text_on_path, new MSSIMComparer(0.6)));
    }

    @Test
    public void testBasicColorXfermode() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.drawColor(Color.GRAY);
                    canvas.drawColor(Color.BLUE, PorterDuff.Mode.MULTIPLY);
                })
                .runWithComparer(mExactComparer);
    }

    @Test
    public void testBasicColorBlendMode() {
        createTest().addCanvasClient((canvas, width, height) -> {
            canvas.drawColor(Color.GRAY);
            canvas.drawColor(Color.BLUE, BlendMode.MULTIPLY);
        }).runWithComparer(mExactComparer);
    }

    @Test
    public void testBluePaddedSquare() {
        final NinePatchDrawable ninePatchDrawable = (NinePatchDrawable)
            getActivity().getResources().getDrawable(R.drawable.blue_padded_square);
        ninePatchDrawable.setBounds(0, 0, 90, 90);

        BitmapVerifier verifier = new RectVerifier(Color.WHITE, Color.BLUE,
                new Rect(10, 10, 80, 80));

        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.drawColor(Color.WHITE);
                    Paint p = new Paint();
                    p.setColor(Color.BLUE);
                    canvas.drawRect(10, 10, 80, 80, p);
                })
                .addCanvasClient(
                        (canvas, width, height) -> ninePatchDrawable.draw(canvas))
                .addLayout(R.layout.blue_padded_square, null)
                .runWithVerifier(verifier);
    }

    @Test
    public void testEmptyLayer() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.drawColor(Color.CYAN);
                    Paint p = new Paint();
                    p.setColor(Color.BLACK);
                    canvas.saveLayer(10, 10, 80, 80, p);
                    canvas.restore();
                })
                .runWithComparer(mExactComparer);
    }

    @Test
    public void testSaveLayerRounding() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.saveLayerAlpha(10.5f, 10.5f, 79.5f, 79.5f, 255);
                    canvas.drawRect(20, 20, 70, 70, new Paint());
                    canvas.restore();
                })
                .runWithVerifier(new RectVerifier(Color.WHITE, Color.BLACK,
                        new Rect(20, 20, 70, 70)));
    }

    @Test
    public void testUnclippedSaveLayerRounding() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.saveLayerAlpha(10.5f, 10.5f, 79.5f, 79.5f, 255);
                    canvas.drawRect(20, 20, 70, 70, new Paint());
                    canvas.restore();
                })
                .runWithVerifier(new RectVerifier(Color.WHITE, Color.BLACK,
                        new Rect(20, 20, 70, 70)));
    }

    @Test
    public void testBlackTriangleVertices() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    float[] vertices = new float[6];
                    vertices[0] = width / 2.0f;
                    vertices[1] = 0;
                    vertices[2] = width;
                    vertices[3] = height;
                    vertices[4] = 0;
                    vertices[5] = height;
                    int[] colors = new int[] { Color.BLACK, Color.BLACK, Color.BLACK };
                    canvas.drawVertices(Canvas.VertexMode.TRIANGLES, vertices.length, vertices, 0,
                            null, 0, colors, 0, null, 0, 0,
                            new Paint());
                })
                .runWithComparer(mExactComparer);
    }

    @Test
    public void testBlackTriangleVertices2() {
        BitmapVerifier verifier = new PerPixelBitmapVerifier() {
            @Override
            protected boolean verifyPixel(int x, int y, int observedColor) {
                // The CanvasClient will draw the following black triangle on a white
                // background:
                //               (40, 0)
                //
                //
                //
                //
                // (0, 80)                      (80, 0)
                if (y >= 80) {
                    // Below the triangle is white.
                    return CompareUtils.verifyPixelWithThreshold(observedColor, Color.WHITE, 0);
                } else if (x < 40) {
                    // The line on the left is
                    //    y = -2x + 80
                    // Above is white, below is black. Give some leeway for
                    // antialiasing.
                    if (y < -2 * x + 80 - 1) {
                        return CompareUtils.verifyPixelWithThreshold(observedColor, Color.WHITE, 0);
                    } else if (y > -2 * x + 80 + 1) {
                        return CompareUtils.verifyPixelWithThreshold(observedColor, Color.BLACK, 0);
                    }
                } else {
                    // The line on the right is
                    //    y = 2x - 80
                    // Above is white, below is black. Give some leeway for
                    // antialiasing.
                    if (y < 2 * x - 80 - 1) {
                        return CompareUtils.verifyPixelWithThreshold(observedColor, Color.WHITE, 0);
                    } else if (y > 2 * x - 80 + 1) {
                        return CompareUtils.verifyPixelWithThreshold(observedColor, Color.BLACK, 0);
                    }
                }
                // Ignore points very close to the line.
                return true;
            }
        };

        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.drawColor(Color.WHITE);

                    float[] vertices = new float[6];
                    vertices[0] = 40;
                    vertices[1] = 0;
                    vertices[2] = 80;
                    vertices[3] = 80;
                    vertices[4] = 0;
                    vertices[5] = 80;
                    int[] colors = new int[] { Color.BLACK, Color.BLACK, Color.BLACK };
                    canvas.drawVertices(Canvas.VertexMode.TRIANGLES, vertices.length, vertices, 0,
                            null, 0, colors, 0, null, 0, 0,
                            new Paint());
                })
                .runWithVerifier(verifier);
    }

    @Test
    @ApiTest(apis = {"android.graphics.Canvas#drawVertices"})
    public void testDrawVertices_doNotBlendColorsWithoutShader() {
        BitmapVerifier verifier = new ColorVerifier(Color.BLUE);

        createTest()
                .addCanvasClient(
                        (canvas, width, height) -> {
                            float[] verts =
                                    new float[] {
                                        width, 0, width, height, 0, 0, 0, height,
                                    };
                            int[] vertColors =
                                    new int[] {Color.BLUE, Color.BLUE, Color.BLUE, Color.BLUE};
                            // Paint color should be ignored and not blend with vertex color.
                            Paint paint = new Paint();
                            paint.setColor(Color.RED);
                            canvas.drawVertices(
                                    Canvas.VertexMode.TRIANGLE_STRIP,
                                    verts.length,
                                    verts,
                                    0,
                                    null,
                                    0,
                                    vertColors,
                                    0,
                                    null,
                                    0,
                                    0,
                                    paint);
                        })
                .runWithVerifier(verifier);
    }

    @Test
    @ApiTest(apis = {"android.graphics.Canvas#drawVertices"})
    public void testDrawVertices_blendVertexAndShaderColors() {
        Color vertexColor = Color.valueOf(0.8f, 0.6f, 0.4f);
        Color shaderColor = Color.valueOf(0.5f, 0.5f, 0.5f);
        // drawVertices should blend vertex color and shader color with kModulate.
        Color modulatedColor =
                Color.valueOf(
                        vertexColor.red() * shaderColor.red(),
                        vertexColor.green() * shaderColor.green(),
                        vertexColor.blue() * shaderColor.blue());
        BitmapVerifier verifier = new ColorVerifier(modulatedColor.toArgb());

        createTest()
                .addCanvasClient(
                        (canvas, width, height) -> {
                            float[] verts =
                                    new float[] {
                                        width, 0, width, height, 0, 0, 0, height,
                                    };
                            int[] vertColors = new int[4];
                            Arrays.fill(vertColors, vertexColor.toArgb());
                            int[] shaderColors = new int[2];
                            Arrays.fill(shaderColors, shaderColor.toArgb());

                            Paint paint = new Paint();
                            paint.setShader(
                                    new LinearGradient(
                                            0,
                                            0,
                                            width,
                                            height,
                                            shaderColors,
                                            null,
                                            Shader.TileMode.REPEAT));
                            canvas.drawVertices(
                                    Canvas.VertexMode.TRIANGLE_STRIP,
                                    verts.length,
                                    verts,
                                    0,
                                    verts,
                                    0,
                                    vertColors,
                                    0,
                                    null,
                                    0,
                                    0,
                                    paint);
                        })
                .runWithVerifier(verifier);
    }

    @Test
    @ApiTest(apis = {"android.graphics.Canvas#drawVertices"})
    public void testDrawVertices_ignoreShaderIfTexsNotSet() {
        Color vertexColor = Color.valueOf(0.8f, 0.6f, 0.4f);
        Color shaderColor = Color.valueOf(0.5f, 0.5f, 0.5f); // Should be ignored.
        BitmapVerifier verifier = new ColorVerifier(vertexColor.toArgb());

        createTest()
                .addCanvasClient(
                        (canvas, width, height) -> {
                            float[] verts =
                                    new float[] {
                                        width, 0, width, height, 0, 0, 0, height,
                                    };
                            int[] vertColors = new int[4];
                            Arrays.fill(vertColors, vertexColor.toArgb());
                            int[] shaderColors = new int[2];
                            Arrays.fill(shaderColors, shaderColor.toArgb());

                            Paint paint = new Paint();
                            paint.setShader(
                                    new LinearGradient(
                                            0,
                                            0,
                                            width,
                                            height,
                                            shaderColors,
                                            null,
                                            Shader.TileMode.REPEAT));
                            canvas.drawVertices(
                                    Canvas.VertexMode.TRIANGLE_STRIP,
                                    verts.length,
                                    verts,
                                    0,
                                    null,
                                    0,
                                    vertColors,
                                    0,
                                    null,
                                    0,
                                    0,
                                    paint);
                        })
                .runWithVerifier(verifier);
    }

    @Test
    public void testColorLongs() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.drawColor(Color.pack(0.5f, 0.3f, 0.1f, 1.0f,
                                ColorSpace.get(ColorSpace.Named.DISPLAY_P3)));
                    canvas.drawColor(Color.pack(0.2f, 0.2f, 0.2f, 1.0f,
                                ColorSpace.get(ColorSpace.Named.DISPLAY_P3)), BlendMode.PLUS);
                    Paint p = new Paint();
                    p.setColor(Color.pack(0.7f, 0.9f, 0.4f, 1.0f,
                                ColorSpace.get(ColorSpace.Named.DISPLAY_P3)));
                    canvas.drawRect(20, 20, 70, 70, p);
                })
                .runWithComparer(mExactComparer);
    }
}
