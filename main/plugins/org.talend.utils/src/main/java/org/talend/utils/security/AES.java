// ============================================================================
//
// Copyright (C) 2006-2016 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.utils.security;

import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * created by zwli on Feb 27, 2013 Detailed comment
 */
public class AES {

    static {
        if (null == Security.getProvider("BC")) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static Logger log = Logger.getLogger(AES.class);

    private static final String RANDOM_SHA1PRNG = "SHA1PRNG";

    private static final String ENCRYPTION_ALGORITHM = "AES";

    private static final String EMPTY_STRING = "";

    private static final String UTF8 = "UTF8";

    // 8-byte
    private static final byte[] KeyValues = { (byte) 0xA9, (byte) 0x9B, (byte) 0xC8, (byte) 0x32, (byte) 0x56, (byte) 0x35,
            (byte) 0xE3, (byte) 0x03 };

    private Cipher ecipher;

    private Cipher dcipher;

    public static AES getInstance() {
        return new AES();
    }

    public AES() {
        try {
            // TDI-28380: Database password in tac db configuration page becomes empty once restart tomcat on Solaris.
            // TDI-30348: Whole tac configuration lost for the passwords.

            Provider p = Security.getProvider("BC");
            KeyGenerator keyGen = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM, p);

            SecureRandom random = SecureRandom.getInstance(RANDOM_SHA1PRNG);
            random.setSeed(KeyValues);
            keyGen.init(128, random);

            Key key = keyGen.generateKey();

            ecipher = Cipher.getInstance(ENCRYPTION_ALGORITHM, p);
            dcipher = Cipher.getInstance(ENCRYPTION_ALGORITHM, p);

            ecipher.init(Cipher.ENCRYPT_MODE, key);
            dcipher.init(Cipher.DECRYPT_MODE, key);
        } catch (Exception e) {
            // log the error to avoid that break GWT service
            log.error(e.getMessage(), e);
        }
    }

    public String encrypt(String data) throws IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException {
        if (EMPTY_STRING.equals(data)) {
            return EMPTY_STRING;
        }
        byte[] enc = ecipher.doFinal(data.getBytes(UTF8));
        String encryptedData = new String(Base64.encodeBase64(enc), UTF8);
        return encryptedData;
    }

    public String decrypt(String encryptedData) throws UnsupportedEncodingException, IllegalBlockSizeException,
            BadPaddingException {
        if (EMPTY_STRING.equals(encryptedData)) {
            return EMPTY_STRING;
        }
        byte[] dec = Base64.decodeBase64(encryptedData.getBytes(UTF8));
        dec = dcipher.doFinal(dec);
        String decryptedData = new String(dec, UTF8);
        return decryptedData;
    }

    public static void main(String[] args) {
        AES aes = new AES();
        String[] arr = { "bt4AUzTV14kK8FwkcK/BNg==", "3IqdoqEElsy8Dzz9iP3HVQ==", "w4AXOA1a34afqqnlmVLB4A==",
                "m9Ut0k3oP5pLE2BH1r9xQA==", "zPfoS7aDB2mNUrpRfbfwcOza/VXudqA9QYULYn4xTb8=",
                "3mTjF2v1D4ZYqnJleFKl/wFybG4/24iyhCFKyEuveDY=" };
        try {
            for (String t : arr) {
                System.out.println(aes.decrypt(t));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
