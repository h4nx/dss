package eu.europa.esig.dss.validation.process.bbb.xcv;

import java.util.Date;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import eu.europa.esig.dss.jaxb.detailedreport.XmlXCV;
import eu.europa.esig.dss.jaxb.diagnostic.XmlChainCertificate;
import eu.europa.esig.dss.validation.policy.Context;
import eu.europa.esig.dss.validation.policy.SubContext;
import eu.europa.esig.dss.validation.policy.ValidationPolicy;
import eu.europa.esig.dss.validation.process.Chain;
import eu.europa.esig.dss.validation.process.ChainItem;
import eu.europa.esig.dss.validation.process.bbb.sav.checks.CryptographicCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.CertificateExpirationCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.CertificateSignatureValidCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.IntermediateCertificateRevokedCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.KeyUsageCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.ProspectiveCertificateChainCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.RevocationDataAvailableCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.RevocationDataTrustedCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.RevocationFreshnessCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.SigningCertificateIssuedToLegalPersonCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.SigningCertificateOnHoldCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.SigningCertificateQualifiedCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.SigningCertificateRevokedCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.SigningCertificateSupportedBySSCDCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.SigningCertificateTSLStatusAndValidityCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.SigningCertificateTSLStatusCheck;
import eu.europa.esig.dss.validation.process.bbb.xcv.checks.SigningCertificateTSLValidityCheck;
import eu.europa.esig.dss.validation.reports.wrapper.CertificateWrapper;
import eu.europa.esig.dss.validation.reports.wrapper.DiagnosticData;
import eu.europa.esig.dss.validation.reports.wrapper.RevocationWrapper;
import eu.europa.esig.dss.validation.reports.wrapper.TokenProxy;
import eu.europa.esig.jaxb.policy.CryptographicConstraint;
import eu.europa.esig.jaxb.policy.LevelConstraint;
import eu.europa.esig.jaxb.policy.MultiValuesConstraint;
import eu.europa.esig.jaxb.policy.TimeConstraint;

/**
 * 5.2.6 X.509 certificate validation This building block validates the signing
 * certificate at current time.
 */
public class X509CertificateValidation extends Chain<XmlXCV> {

	private final DiagnosticData diagnosticData;
	private final CertificateWrapper currentCertificate;
	private final Date currentTime;

	private final Context context;
	private final ValidationPolicy validationPolicy;

	public X509CertificateValidation(DiagnosticData diagnosticData, CertificateWrapper currentCertificate, Date currentTime, Context context,
			ValidationPolicy validationPolicy) {
		super(new XmlXCV());

		this.diagnosticData = diagnosticData;
		this.currentCertificate = currentCertificate;
		this.currentTime = currentTime;

		this.context = context;
		this.validationPolicy = validationPolicy;

	}

	@Override
	protected void initChain() {

		ChainItem<XmlXCV> item = firstItem = prospectiveCertificateChain();

		// Checks SIGNING_CERT

		item = item.setNextItem(certificateExpiration(currentCertificate, SubContext.SIGNING_CERT));

		item = item.setNextItem(keyUsage(currentCertificate, SubContext.SIGNING_CERT));

		item = item.setNextItem(certificateSignatureValid(currentCertificate, SubContext.SIGNING_CERT));

		item = item.setNextItem(certificateCryptographic(currentCertificate, context, SubContext.SIGNING_CERT));

		if (!currentCertificate.isTrusted()) {
			item = item.setNextItem(revocationDataAvailable(currentCertificate, SubContext.SIGNING_CERT));

			item = item.setNextItem(revocationDataTrusted(currentCertificate, SubContext.SIGNING_CERT));

			item = item.setNextItem(revocationFreshness(currentCertificate));

			item = item.setNextItem(signingCertificateRevoked(currentCertificate, SubContext.SIGNING_CERT));

			item = item.setNextItem(signingCertificateOnHold(currentCertificate, SubContext.SIGNING_CERT));

			item = item.setNextItem(signingCertificateInTSLValidity(currentCertificate));

			item = item.setNextItem(signingCertificateTSLStatus(currentCertificate));

			item = item.setNextItem(signingCertificateTSLStatusAndValidity(currentCertificate));

			// check cryptographic constraints for the revocation token
			RevocationWrapper revocationData = currentCertificate.getRevocationData();
			if (revocationData != null) {
				item = item.setNextItem(certificateCryptographic(revocationData, Context.REVOCATION, SubContext.SIGNING_CERT));
			}
		}

		// Check CA_CERTIFICATEs
		List<XmlChainCertificate> certificateChainList = currentCertificate.getCertificateChain();
		if (CollectionUtils.isNotEmpty(certificateChainList)) {
			for (XmlChainCertificate chainCertificate : certificateChainList) {
				CertificateWrapper certificate = diagnosticData.getUsedCertificateByIdNullSafe(chainCertificate.getId());

				item = item.setNextItem(certificateExpiration(certificate, SubContext.CA_CERTIFICATE));

				item = item.setNextItem(keyUsage(certificate, SubContext.CA_CERTIFICATE));

				item = item.setNextItem(certificateSignatureValid(certificate, SubContext.CA_CERTIFICATE));

				item = item.setNextItem(intermediateCertificateRevoked(certificate, SubContext.CA_CERTIFICATE));

				item = item.setNextItem(certificateCryptographic(certificate, context, SubContext.CA_CERTIFICATE));

				// check cryptographic constraints for the revocation token
				RevocationWrapper revocationData = certificate.getRevocationData();
				if (revocationData != null) {
					item = item.setNextItem(certificateCryptographic(revocationData, Context.REVOCATION, SubContext.CA_CERTIFICATE));
				}

			}
		}

		// These constraints apply only to the main signature
		if (Context.SIGNATURE.equals(context)) {
			item = item.setNextItem(signingCertificateQualified(currentCertificate));

			item = item.setNextItem(signingCertificateSupportedBySSCD(currentCertificate));

			item = item.setNextItem(signingCertificateIssuedToLegalPerson(currentCertificate));
		}

	}

	private ChainItem<XmlXCV> prospectiveCertificateChain() {
		LevelConstraint constraint = validationPolicy.getProspectiveCertificateChainConstraint(context);
		return new ProspectiveCertificateChainCheck(result, currentCertificate, diagnosticData, constraint);
	}

	private ChainItem<XmlXCV> certificateExpiration(CertificateWrapper certificate, SubContext subContext) {
		LevelConstraint constraint = validationPolicy.getSigningCertificateExpirationConstraint(context, subContext);
		return new CertificateExpirationCheck(result, certificate, currentTime, constraint);
	}

	private ChainItem<XmlXCV> keyUsage(CertificateWrapper certificate, SubContext subContext) {
		MultiValuesConstraint constraint = validationPolicy.getSigningCertificateKeyUsageConstraint(context, subContext);
		return new KeyUsageCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> certificateSignatureValid(CertificateWrapper certificate, SubContext subContext) {
		LevelConstraint constraint = validationPolicy.getCertificateSignatureConstraint(context, subContext);
		return new CertificateSignatureValidCheck<XmlXCV>(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> revocationDataAvailable(CertificateWrapper certificate, SubContext subContext) {
		LevelConstraint constraint = validationPolicy.getRevocationDataAvailableConstraint(context, subContext);
		return new RevocationDataAvailableCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> revocationDataTrusted(CertificateWrapper certificate, SubContext subContext) {
		LevelConstraint constraint = validationPolicy.getRevocationDataIsTrustedConstraint(context, subContext);
		return new RevocationDataTrustedCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> revocationFreshness(CertificateWrapper certificate) {
		TimeConstraint revocationFreshnessConstraints = validationPolicy.getRevocationFreshnessConstraint();
		return new RevocationFreshnessCheck(result, certificate, currentTime, revocationFreshnessConstraints);
	}

	private ChainItem<XmlXCV> signingCertificateRevoked(CertificateWrapper certificate, SubContext subContext) {
		LevelConstraint constraint = validationPolicy.getCertificateRevokedConstraint(context, subContext);
		return new SigningCertificateRevokedCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> intermediateCertificateRevoked(CertificateWrapper certificate, SubContext subContext) {
		LevelConstraint constraint = validationPolicy.getCertificateRevokedConstraint(context, subContext);
		return new IntermediateCertificateRevokedCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> signingCertificateOnHold(CertificateWrapper certificate, SubContext subContext) {
		LevelConstraint constraint = validationPolicy.getSigningCertificateOnHoldConstraint(context, subContext);
		return new SigningCertificateOnHoldCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> signingCertificateInTSLValidity(CertificateWrapper certificate) {
		LevelConstraint constraint = validationPolicy.getSigningCertificateTSLValidityConstraint(context);
		return new SigningCertificateTSLValidityCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> signingCertificateTSLStatus(CertificateWrapper certificate) {
		LevelConstraint constraint = validationPolicy.getSigningCertificateTSLStatusConstraint(context);
		return new SigningCertificateTSLStatusCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> signingCertificateTSLStatusAndValidity(CertificateWrapper certificate) {
		LevelConstraint constraint = validationPolicy.getSigningCertificateTSLStatusAndValidityConstraint(context);
		return new SigningCertificateTSLStatusAndValidityCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> certificateCryptographic(TokenProxy token, Context context, SubContext subcontext) {
		CryptographicConstraint cryptographicConstraint = validationPolicy.getCertificateCryptographicConstraint(context, subcontext);
		return new CryptographicCheck<XmlXCV>(result, token, currentTime, cryptographicConstraint);
	}

	private ChainItem<XmlXCV> signingCertificateQualified(CertificateWrapper certificate) {
		LevelConstraint constraint = validationPolicy.getSigningCertificateQualificationConstraint(context);
		return new SigningCertificateQualifiedCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> signingCertificateSupportedBySSCD(CertificateWrapper certificate) {
		LevelConstraint constraint = validationPolicy.getSigningCertificateSupportedBySSCDConstraint(context);
		return new SigningCertificateSupportedBySSCDCheck(result, certificate, constraint);
	}

	private ChainItem<XmlXCV> signingCertificateIssuedToLegalPerson(CertificateWrapper certificate) {
		LevelConstraint constraint = validationPolicy.getSigningCertificateIssuedToLegalPersonConstraint(context);
		return new SigningCertificateIssuedToLegalPersonCheck(result, certificate, constraint);
	}

}
