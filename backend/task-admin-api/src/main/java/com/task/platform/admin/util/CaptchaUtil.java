package com.task.platform.admin.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 图形验证码工具
 * 生成 4 位随机字符验证码图片，返回 Base64 编码
 */
public class CaptchaUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去掉易混淆的 0/O/1/I

    /**
     * 生成验证码
     * @return CaptchaResult(code=验证码文字, base64=图片Base64)
     */
    public static CaptchaResult generate(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 背景
        g.setColor(new Color(240, 245, 250));
        g.fillRect(0, 0, width, height);

        // 干扰线
        g.setColor(new Color(180, 180, 200));
        for (int i = 0; i < 4; i++) {
            int x1 = RANDOM.nextInt(width);
            int y1 = RANDOM.nextInt(height);
            int x2 = RANDOM.nextInt(width);
            int y2 = RANDOM.nextInt(height);
            g.drawLine(x1, y1, x2, y2);
        }

        // 生成 4 位随机码
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }

        // 绘制文字
        g.setFont(new Font("Arial", Font.BOLD, 24));
        for (int i = 0; i < 4; i++) {
            String c = String.valueOf(code.charAt(i));
            // 每个字符随机颜色和旋转
            g.setColor(new Color(
                    RANDOM.nextInt(80),
                    RANDOM.nextInt(80),
                    RANDOM.nextInt(160) + 80
            ));
            int x = 8 + i * (width / 4) + RANDOM.nextInt(5) - 2;
            int y = 28 + RANDOM.nextInt(6) - 3;
            g.drawString(c, x, y);
        }

        // 干扰点
        for (int i = 0; i < 30; i++) {
            g.setColor(new Color(RANDOM.nextInt(200), RANDOM.nextInt(200), RANDOM.nextInt(200)));
            g.fillRect(RANDOM.nextInt(width), RANDOM.nextInt(height), 2, 2);
        }

        g.dispose();

        // 转 Base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "PNG", baos);
        } catch (IOException e) {
            throw new RuntimeException("生成验证码失败", e);
        }
        String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());

        return new CaptchaResult(code.toString(), base64);
    }

    public static class CaptchaResult {
        private final String code;
        private final String base64;

        public CaptchaResult(String code, String base64) {
            this.code = code;
            this.base64 = base64;
        }

        public String getCode() { return code; }
        public String getBase64() { return base64; }
    }
}
