package com.example.laive.broadcast.realtime.domain.highlight;

import java.util.Arrays;

/**
 * 최근 engagement 값들의 링버퍼로 median·MAD 기반 z-score를 계산한다(HIGHLIGHT_SPEC §2).
 *
 * <p>평균·표준편차가 아니라 median·MAD인 이유: 하이라이트 자체가 극단값이라
 * 평균과 표준편차는 그 극단값에 끌려가 baseline이 오염된다. median과 MAD는
 * 절반 미만의 오염에 흔들리지 않는다.
 *
 * <p>매 틱 O(n log n) 정렬이 돌지만 n ≤ 900(15분)에 1초 주기라 부담이 없다.
 *
 * <p>스레드 안전하지 않다. 호출자(세션 상태)가 동기화한다.
 */
public class BaselineBuffer {

    /** 정규분포에서 MAD를 표준편차 스케일로 보정하는 상수다. */
    private static final double MAD_SCALE = 1.4826;

    private final double[] values;
    private int size;
    private int next;

    public BaselineBuffer(int capacity) {
        this.values = new double[capacity];
    }

    public void add(double engagement) {
        values[next] = engagement;
        next = (next + 1) % values.length;
        size = Math.min(size + 1, values.length);
    }

    public int size() {
        return size;
    }

    /**
     * engagement의 z-score를 계산한다. baseline이 비어 있으면 호출하면 안 된다.
     *
     * @param madEpsilon MAD 하한(가드 2). 값 절반 이상이 같으면 MAD가 0이 되어
     *                   0으로 나누게 된다
     */
    public double zScore(double engagement, double madEpsilon) {
        double[] snapshot = Arrays.copyOf(values, size);
        Arrays.sort(snapshot);
        double median = medianOfSorted(snapshot);

        double[] deviations = new double[size];
        for (int i = 0; i < size; i++) {
            deviations[i] = Math.abs(snapshot[i] - median);
        }
        Arrays.sort(deviations);
        double mad = Math.max(medianOfSorted(deviations), madEpsilon);

        return (engagement - median) / (MAD_SCALE * mad);
    }

    private static double medianOfSorted(double[] sorted) {
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return sorted[mid];
        }
        return (sorted[mid - 1] + sorted[mid]) / 2.0;
    }
}
