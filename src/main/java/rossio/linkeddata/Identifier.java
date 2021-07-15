package rossio.linkeddata;

import javax.servlet.http.HttpServletRequest;

import org.apache.jena.riot.Lang;

import rossio.util.RdfUtil;

public class Identifier {
	public String resourceType;
	public String localIdentifier;
	
	public String acceptHeader;

	public String rediretTo;
	public Lang sendDataAs;
	
	String httpRequestPath;
	
	public String  errorMessage;
	public Integer httpStatusCode;
	
	public Identifier(HttpServletRequest req) {
		httpRequestPath=req.getPathInfo();
		if(httpRequestPath.equals("")){
			errorMessage="Identificador com estrutura inválida.";
			return;
		}
		String httpGet=httpRequestPath.substring(1);
		int idx=httpGet.indexOf('/');
		if(idx<0) {
			resourceType=httpGet;
			localIdentifier="";
		} else {
			resourceType=httpGet.substring(0, idx);
			localIdentifier=httpGet.substring(idx+1);
			localIdentifier=localIdentifier.trim();
		}

		if(isValidResourceType()) {
			acceptHeader=req.getHeader("Accept");
			sendDataAs=acceptHeader==null ? null : RdfUtil.fromMimeType(acceptHeader);
			if(acceptHeader==null || sendDataAs==null) {
				if(localIdentifier.toLowerCase().endsWith(".rdf")) {
					acceptHeader=Lang.RDFXML.getContentType().getContentTypeStr();
					sendDataAs=Lang.RDFXML;
					localIdentifier=localIdentifier.substring(0, localIdentifier.length()-4);
				} else if(localIdentifier.toLowerCase().endsWith(".ttl")) {
					acceptHeader=Lang.TURTLE.getContentType().getContentTypeStr();
					sendDataAs=Lang.TURTLE;
					localIdentifier=localIdentifier.substring(0, localIdentifier.length()-4);
				} else if(localIdentifier.toLowerCase().endsWith(".n3")) {
					acceptHeader=Lang.N3.getContentType().getContentTypeStr();
					sendDataAs=Lang.N3;
					localIdentifier=localIdentifier.substring(0, localIdentifier.length()-3);
				} 		
			}
		} else {
			errorMessage="Identificador não existente (tipo de recurso desconhecido - '"+resourceType+"')";
			return;
		}
	}

	private boolean isValidResourceType() {
		return resourceType!=null && (resourceType.equals("item") || resourceType.equals("conjunto-de-dados")
				 || resourceType.equals("coisa") || resourceType.equals("agentes") 
				 || resourceType.equals("lugares")  || resourceType.equals("periodos") 
				 || resourceType.equals("tesauro"))	;
	}

	public String getRdfResourceUri() {
		if(resourceType.equals("item") || resourceType.equals("conjunto-de-dados") || resourceType.equals("coisa"))
			return "http://dados.rossio.fcsh.unl.pt"+httpRequestPath;
		else
			return "http://vocabs.rossio.fcsh.unl.pt"+httpRequestPath;
	}

	@Override
	public String toString() {
		return "Identifier [resourceType=" + resourceType + ", localIdentifier=" + localIdentifier + ", acceptHeader="
				+ acceptHeader + ", rediretTo=" + rediretTo + ", sendDataAs=" + sendDataAs + ", httpRequestPath="
				+ httpRequestPath + ", errorMessage=" + errorMessage + ", httpStatusCode=" + httpStatusCode + "]";
	}
	


	
	
}
