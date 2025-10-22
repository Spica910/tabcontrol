package com.automation.helper;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class ImageMatcher {
    private static final String TAG = "ImageMatcher";
    private static final double DEFAULT_THRESHOLD = 0.8; // 80% similarity

    // Find template image in source image
    public static MatchResult findImage(Bitmap source, Bitmap template) {
        return findImage(source, template, DEFAULT_THRESHOLD);
    }

    public static MatchResult findImage(Bitmap source, Bitmap template, double threshold) {
        if (source == null || template == null) {
            Log.e(TAG, "Source or template is null");
            return null;
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int templateWidth = template.getWidth();
        int templateHeight = template.getHeight();

        if (templateWidth > sourceWidth || templateHeight > sourceHeight) {
            Log.e(TAG, "Template is larger than source");
            return null;
        }

        MatchResult bestMatch = null;
        double bestScore = 0;

        // Slide template over source image
        for (int y = 0; y <= sourceHeight - templateHeight; y += 5) { // Step by 5 for performance
            for (int x = 0; x <= sourceWidth - templateWidth; x += 5) {
                double score = compareRegion(source, template, x, y);
                if (score > bestScore && score >= threshold) {
                    bestScore = score;
                    bestMatch = new MatchResult(x, y, templateWidth, templateHeight, score);
                }
            }
        }

        if (bestMatch != null) {
            // Refine the match with finer steps around the best position
            int refineX = Math.max(0, bestMatch.x - 5);
            int refineY = Math.max(0, bestMatch.y - 5);
            int refineEndX = Math.min(sourceWidth - templateWidth, bestMatch.x + 5);
            int refineEndY = Math.min(sourceHeight - templateHeight, bestMatch.y + 5);

            for (int y = refineY; y <= refineEndY; y++) {
                for (int x = refineX; x <= refineEndX; x++) {
                    double score = compareRegion(source, template, x, y);
                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = new MatchResult(x, y, templateWidth, templateHeight, score);
                    }
                }
            }

            Log.d(TAG, "Match found at (" + bestMatch.x + ", " + bestMatch.y + ") with score: " + bestScore);
        } else {
            Log.d(TAG, "No match found above threshold: " + threshold);
        }

        return bestMatch;
    }

    // Compare template with a region in source image
    private static double compareRegion(Bitmap source, Bitmap template, int startX, int startY) {
        int templateWidth = template.getWidth();
        int templateHeight = template.getHeight();

        long totalDiff = 0;
        long maxDiff = 255 * 3 * templateWidth * templateHeight; // RGB components

        for (int y = 0; y < templateHeight; y++) {
            for (int x = 0; x < templateWidth; x++) {
                int sourcePixel = source.getPixel(startX + x, startY + y);
                int templatePixel = template.getPixel(x, y);

                int rDiff = Math.abs(Color.red(sourcePixel) - Color.red(templatePixel));
                int gDiff = Math.abs(Color.green(sourcePixel) - Color.green(templatePixel));
                int bDiff = Math.abs(Color.blue(sourcePixel) - Color.blue(templatePixel));

                totalDiff += rDiff + gDiff + bDiff;
            }
        }

        // Convert to similarity score (0 to 1)
        return 1.0 - ((double) totalDiff / maxDiff);
    }

    public static class MatchResult {
        public int x, y;           // Top-left corner
        public int width, height;   // Template dimensions
        public double score;        // Similarity score

        public MatchResult(int x, int y, int width, int height, double score) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.score = score;
        }

        // Get center point
        public int getCenterX() {
            return x + width / 2;
        }

        public int getCenterY() {
            return y + height / 2;
        }
    }
}
