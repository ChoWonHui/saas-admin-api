package com.saas.admin.tenant;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

/** 문자열(주문 URL)을 QR 코드 PNG 로 만든다. */
public final class QrGenerator {

    private QrGenerator() {
    }

    public static byte[] png(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(MatrixToImageWriter.toBufferedImage(matrix), "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "QR 코드 생성에 실패했습니다.");
        }
    }
}
