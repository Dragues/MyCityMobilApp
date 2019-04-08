package com.example.ilya.mycitymobilapp;

import android.graphics.Point;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.*;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {

    private ImageView taxiView;
    private ImageView humanView;
    private RadioButton simpleRadio;
    private FrameLayout mainFrame;
    private int height;
    private int width;
    private Subscription subscription;
    private Subscription subscription2;
    private int currentRotate = 0;
    private boolean hasRequest;
    private int hordaLen = 4;
    private double radiusHardAlgorithm;
    private int degreeStep = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        taxiView = findViewById(R.id.taxi_view);
        humanView = findViewById(R.id.human_view);
        simpleRadio = findViewById(R.id.simple_radio);
        taxiView.setOnClickListener(v -> {
            int randX = (int) (Math.random() * width * 0.8);
            int randY = (int) (Math.random() * height * 0.8);
            setTaxyPosition(randX, randY);

        });
        mainFrame = findViewById(R.id.main_frame);


        // из прямоугольного треугольника выссчитываем в круге
        // была мысль выссчитывать через теорему косинусов минимальный радиус зная хорду и угол (step) но так проще
        radiusHardAlgorithm = hordaLen / (2 * Math.sin(Math.toRadians((double)degreeStep / 2)));

        final ViewTreeObserver observer = mainFrame.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(
                () -> {
                    height = mainFrame.getHeight();
                    width = mainFrame.getWidth();
                });

        mainFrame.setOnTouchListener((v, event) -> {
            if (hasRequest)
                return true;
            hasRequest = true;

            int touchX = (int) event.getX();
            int touchY = (int) event.getY();

            setHumanPosition(touchX, touchY);

            FrameLayout.LayoutParams paramsStart = (FrameLayout.LayoutParams) taxiView.getLayoutParams();
            int taxiX = paramsStart.leftMargin;
            int taxiY = paramsStart.topMargin;

            double betweenRange = Math.sqrt(Math.pow(touchX - taxiX, 2) + Math.pow(touchY - taxiY, 2));

            // отправляем машинку покататься  на нужно расстояние чтобы потом сделать петлю
            if (betweenRange < radiusHardAlgorithm * 2 && !simpleRadio.isChecked()) {
                while (betweenRange < radiusHardAlgorithm * 2) {
                    int deltaX = (int) (hordaLen * Math.cos((double) (90 - currentRotate) * Math.PI / 180)); //  перевожу сам в радианы, не доверяю Math.toRadians
                    int deltaY = (int) (-hordaLen * Math.sin((double) (90 - currentRotate) * Math.PI / 180));
                    taxiX += deltaX;
                    taxiY += deltaY;
                    betweenRange = Math.sqrt(Math.pow(touchX - taxiX, 2) + Math.pow(touchY - taxiY, 2));
                }
                // отправляем к нужной точке
                runToPoint(new Point(taxiX , taxiY), true, new Point(touchX, touchY));
            }
            else {
                // классически запускаем машинку к месту назначения без отъезжания (делаем петлю/разворачиваемся на месте + едем до точки по прямой автомагистрали)
                runStandardTaxiAlgorithm(touchX, touchY);
            }
            return false;
        });
    }

    private void runStandardTaxiAlgorithm(int touchX, int touchY) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) taxiView.getLayoutParams();
        int taxiX = params.leftMargin;
        int taxiY = params.topMargin;
        int futureAngle = (int) (Math.toDegrees(Math.atan2(taxiY - touchY, taxiX - touchX))) - 90;
        ArrayList<Integer> angles = new ArrayList<>();
        if (currentRotate < futureAngle) {
            for (int i = currentRotate; i < currentRotate + 360; i += degreeStep) {
                angles.add(i);
            }
        } else {
            for (int i = currentRotate; i > currentRotate - 360; i -= degreeStep) {
                angles.add(i);
            }
        }

        if (angles.isEmpty())
            return;
        subscription = Observable.just(angles).interval(10, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap((Func1<Long, Observable<Integer>>) ignore -> {
                    Integer stepItem = angles.get(0);
                    angles.remove(0);
                    return Observable.just(stepItem);
                })
                .subscribe(rotation -> {
                    taxiView.setRotation(rotation);
                    FrameLayout.LayoutParams paramsCur = (FrameLayout.LayoutParams) taxiView.getLayoutParams();
                    int curX = paramsCur.leftMargin;
                    int curY = paramsCur.topMargin;
                    int lastAngle = (int) (Math.toDegrees(Math.atan2(curY - touchY, curX - touchX))) - 90;
                    if (simpleRadio.isChecked()) {
                    } else {
                        int deltaX = (int) (hordaLen * Math.cos((double) (90 - rotation) * Math.PI / 180)); //  перевожу сам в радианы,
                                                                                                            // не доверяю Math.toRadians, не критиковать
                        int deltaY = (int) (-hordaLen * Math.sin((double) (90 - rotation) * Math.PI / 180));
                        setTaxyPosition(curX + deltaX,
                                curY + deltaY);
                    }
                    currentRotate = rotation;
                    if (currentRotate == lastAngle && !subscription.isUnsubscribed()) {
                        subscription.unsubscribe();
                        runToPoint(new Point(touchX, touchY));
                    }
                });
        return;
    }

    private void setHumanPosition(int touchX, int touchY) {
        humanView.setVisibility(View.VISIBLE);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) humanView.getLayoutParams();
        params.topMargin = touchY;
        params.leftMargin = touchX;
        humanView.setLayoutParams(params);
    }

    private void setTaxyPosition(int randX, int randY) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) taxiView.getLayoutParams();
        params.topMargin = randY;
        params.leftMargin = randX;
        taxiView.setLayoutParams(params);
    }
    private void runToPoint(Point point) {
        runToPoint(point, false, null);
    }
    private void runToPoint(Point point, boolean runStandardAlgorithmAfter, Point touchPoint) {

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) taxiView.getLayoutParams();
        int taxiX = params.leftMargin;
        int taxiY = params.topMargin;

        Point start = new Point(taxiX, taxiY);
        Point end = point;
        ArrayList<Point> points = new ArrayList<>();
        points.add(start);

        int pointsCount = 50;
        for (int i = 0; i < pointsCount; i++) {
            points.add(new Point(taxiX + i * (end.x - start.x) / pointsCount, taxiY + i * (end.y - start.y) / pointsCount));
        }
        subscription2 = Observable.interval(25, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap((Func1<Long, Observable<Point>>) ignore -> {
                    Point curPos = points.get(0);
                    points.remove(0);
                    return Observable.just(curPos);
                })
                .subscribe(onUpdateItem -> {
                    setTaxyPosition(onUpdateItem.x, onUpdateItem.y);
                    if (points.isEmpty() && !subscription2.isUnsubscribed()) {
                        subscription2.unsubscribe();
                        if (runStandardAlgorithmAfter) {
                            runStandardTaxiAlgorithm(touchPoint.x, touchPoint.y);
                        }
                        else {
                            humanView.setVisibility(View.GONE);
                            hasRequest = false;
                        }
                    }
                });
    }
}
