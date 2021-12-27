package org.kse.gui.actions;

import java.awt.Toolkit;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;

import javax.swing.ImageIcon;

import org.kse.crypto.Password;
import org.kse.crypto.keypair.KeyPairType;
import org.kse.crypto.keypair.KeyPairUtil;
import org.kse.crypto.signing.SignatureType;
import org.kse.gui.KseFrame;
import org.kse.gui.dialogs.DViewJwt;
import org.kse.gui.dialogs.sign.CustomClaim;
import org.kse.gui.dialogs.sign.DSignJwt;
import org.kse.gui.error.DError;
import org.kse.utilities.history.KeyStoreHistory;
import org.kse.utilities.history.KeyStoreState;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTClaimsSet.Builder;
import com.nimbusds.jwt.SignedJWT;

/**
 * Action to create a JWT (JSON Web Token)
 *
 */
public class SignJwtAction extends KeyStoreExplorerAction {

	private static final long serialVersionUID = 1L;

	/**
	 * Construct action.
	 * 
	 * @param kseFrame KeyStore Explorer frame
	 */
	public SignJwtAction(KseFrame kseFrame) {
		super(kseFrame);
		putValue(LONG_DESCRIPTION, res.getString("SignJwtAction.statusbar"));
		putValue(NAME, res.getString("SignJwtAction.text"));
		putValue(SHORT_DESCRIPTION, res.getString("SignJwtAction.tooltip"));
		putValue(SMALL_ICON,
				new ImageIcon(Toolkit.getDefaultToolkit().createImage(getClass().getResource("images/signcrl.png"))));
	}

	@Override
	protected void doAction() {
		try {
			KeyStoreHistory history = kseFrame.getActiveKeyStoreHistory();
			KeyStoreState currentState = history.getCurrentState();

			String alias = kseFrame.getSelectedEntryAlias();

			Password password = getEntryPassword(alias, currentState);

			if (password == null) {
				return;
			}
			KeyStore keyStore = currentState.getKeyStore();

			Provider provider = history.getExplicitProvider();
			PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
			KeyPairType keyPairType = KeyPairUtil.getKeyPairType(privateKey);

			DSignJwt dSignJwt = new DSignJwt(frame, keyPairType, privateKey);
			dSignJwt.setLocationRelativeTo(frame);
			dSignJwt.setVisible(true);
			if (dSignJwt.isOk()) {
				String encodedJWT = signJwt(dSignJwt, privateKey, provider);
				DViewJwt dialog = new DViewJwt(frame, encodedJWT);
				dialog.setLocationRelativeTo(frame);
				dialog.setVisible(true);
			}
		} catch (Exception ex) {
			DError.displayError(frame, ex);
		}
	}

	private String signJwt(DSignJwt dSignJwt, PrivateKey privateKey, Provider provider)
			throws Exception {

		Curve curve = null;
		JWSAlgorithm signatureAlgorithm = null;
		SignatureType signatureType = dSignJwt.getSignatureType();
		switch (signatureType) {
		case SHA256_RSA:
			signatureAlgorithm = JWSAlgorithm.RS256;
			break;
		case SHA384_RSA:
			signatureAlgorithm = JWSAlgorithm.RS384;
			break;
		case SHA512_RSA:
			signatureAlgorithm = JWSAlgorithm.RS512;
			break;
		case SHA256WITHRSAANDMGF1:
			signatureAlgorithm = JWSAlgorithm.PS256;
			break;
		case SHA384WITHRSAANDMGF1:
			signatureAlgorithm = JWSAlgorithm.PS384;
			break;
		case SHA512WITHRSAANDMGF1:
			signatureAlgorithm = JWSAlgorithm.PS512;
			break;
		case SHA256_ECDSA:
			signatureAlgorithm = JWSAlgorithm.ES256;
			curve = Curve.P_256;
			break;
		case SHA384_ECDSA:
			signatureAlgorithm = JWSAlgorithm.ES384;
			curve = Curve.P_384;
			break;
		case SHA512_ECDSA:
			signatureAlgorithm = JWSAlgorithm.ES512;
			curve = Curve.P_521;
			break;
		default:
			break;
		}
		if (signatureAlgorithm == null) {
			throw new NoSuchAlgorithmException(
					signatureType + ": " + res.getString("SignJwtAction.signNotAvailable.message"));
		}
		JWSSigner signer = null;
		switch (signatureType) {
		case SHA256_RSA:
		case SHA384_RSA:
		case SHA512_RSA:
		case SHA256WITHRSAANDMGF1:
		case SHA384WITHRSAANDMGF1:
		case SHA512WITHRSAANDMGF1:
			signer = new RSASSASigner(privateKey);
			break;
		case SHA256_ECDSA:
		case SHA384_ECDSA:
		case SHA512_ECDSA:
			signer = new ECDSASigner(privateKey, curve);
			break;
		default:
			break;
		}
		if (provider != null) {
			signer.getJCAContext().setProvider(provider);
		}

		Builder builder = new JWTClaimsSet.Builder().subject(dSignJwt.getSubject()).issuer(dSignJwt.getIssuer())
				.issueTime(dSignJwt.getIssuedAt()).notBeforeTime(dSignJwt.getNotBefore())
				.audience(dSignJwt.getAudience()).expirationTime(dSignJwt.getExpiration());

		for (CustomClaim claim : dSignJwt.getCustomClaims()) {
			builder.claim(claim.getName(), claim.getValue());
		}
		JWTClaimsSet claimsSet = builder.build();

		SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(signatureAlgorithm).keyID(null).build(), claimsSet);
		signedJWT.sign(signer);

		return signedJWT.serialize();
	}
}
