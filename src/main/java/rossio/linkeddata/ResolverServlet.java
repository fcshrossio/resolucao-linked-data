package rossio.linkeddata;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;

import rossio.data.models.BibFrame;
import rossio.data.models.DcTerms;
import rossio.data.models.Dcat;
import rossio.data.models.Owl;
import rossio.data.models.Rossio;
import rossio.data.models.Skos;
import rossio.dspace.DspaceApiClient;
import rossio.ingest.solr.RepositoryWithSolr;
import rossio.sparql.SparqlClient;
import rossio.util.AccessException;
import rossio.util.Global;
import rossio.util.RdfUtil;
import rossio.util.RdfUtil.Jena;

public class ResolverServlet extends HttpServlet {
	private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(ResolverServlet.class);

	public static File WEBAPP_ROOT;
	public static DspaceApiClient dspaceApiClient;
	public static RepositoryWithSolr repository;
	public static SparqlClient vocabsSparql;
	public static String vocabsHtmlBaseUrl;

	@Override
	public void init(ServletConfig config) throws ServletException {
		try {
			WEBAPP_ROOT = new File(config.getServletContext().getRealPath(""));
			log.info(WEBAPP_ROOT);
			Global.init_componentHttpRequestService();
			dspaceApiClient=new DspaceApiClient(
					config.getInitParameter("rossio.dspace.api.baseUrl"),
					config.getInitParameter("rossio.dspace.api.user"),
					config.getInitParameter("rossio.dspace.api.password")
					);
			repository=new RepositoryWithSolr(config.getInitParameter("rossio.repositorio.solr.baseUrl"));
			vocabsSparql=new SparqlClient(config.getInitParameter("rossio.vocabs.sparql.baseUrl"), "");
			vocabsSparql.setDebug(true);
			vocabsHtmlBaseUrl=config.getInitParameter("rossio.vocabs.html.baseUrl");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	public void service(HttpServletRequest req, HttpServletResponse res) {
		try {
			Identifier id = new Identifier(req);
			log.debug(id);
			System.out.println(id);
			
			Model rdfModel=null;
			if(id.errorMessage==null) try {
				if(id.resourceType.equals("item")) {
					byte[] item = repository.getItem(id.localIdentifier);
					if(item==null) { 
						RDFParser reader = RDFParser.create().lang(Lang.RDFTHRIFT).source(new ByteArrayInputStream(item)).build();
						rdfModel = Jena.createModel();
						reader.parse(rdfModel);
						rdfModel.setNsPrefix("dct", DcTerms.NS);
						rdfModel.setNsPrefix("rossio", Rossio.NS);
					} else
						id.httpStatusCode = 404;
				} else if(id.resourceType.equals("conjunto-de-dados")) {
					//TODO: usar metadados DCAT
					rdfModel=dspaceApiClient.getItemMetadata(id.localIdentifier);
					rdfModel.setNsPrefix("dct", DcTerms.NS);
					rdfModel.setNsPrefix("dcat", Dcat.NS);
				} else if(id.resourceType.equals("agentes") || id.resourceType.equals("tesauro")
						|| id.resourceType.equals("lugares") || id.resourceType.equals("periodos")) {
					rdfModel = Jena.createModel();
					vocabsSparql.createAllStatementsAboutAndReferingResource(id.getRdfResourceUri(), rdfModel, "http://vocabs.rossio.fcsh.unl.pt/"+id.resourceType+"/");
					
//					RdfUtil.printOutRdf(rdfModel);
					if (rdfModel.isEmpty()) {
						id.httpStatusCode=404;
					} else if(id.sendDataAs!=null){
						rdfModel.setNsPrefix("skos", Skos.NS);
						rdfModel.setNsPrefix("owl", Owl.NS);
						rdfModel.setNsPrefix("bf", BibFrame.NS);
					} else if (!id.localIdentifier.isEmpty()){
						if(id.resourceType.equals("agentes")) 
							id.rediretTo=vocabsHtmlBaseUrl+"rossioAgentes/pt/page/?uri="+URLEncoder.encode(id.getRdfResourceUri(), "UTF8");
						else if(id.resourceType.equals("tesauro"))
							id.rediretTo=vocabsHtmlBaseUrl+"rossioTesauro/pt/page/?uri="+URLEncoder.encode(id.getRdfResourceUri(), "UTF8");
						else if(id.resourceType.equals("lugares"))
							id.rediretTo=vocabsHtmlBaseUrl+"rossioLugares/pt/page/?uri="+URLEncoder.encode(id.getRdfResourceUri(), "UTF8");
						else if(id.resourceType.equals("periodos"))
							id.rediretTo=vocabsHtmlBaseUrl+"rossioPeriodos/pt/page/?uri="+URLEncoder.encode(id.getRdfResourceUri(), "UTF8");
					} else {
						if(id.resourceType.equals("agentes")) 
							id.rediretTo=vocabsHtmlBaseUrl+"rossioAgentes/pt/";
						else if(id.resourceType.equals("tesauro"))
							id.rediretTo=vocabsHtmlBaseUrl+"rossioTesauro/pt/";
						else if(id.resourceType.equals("lugares"))
							id.rediretTo=vocabsHtmlBaseUrl+"rossioLugares/pt/";
						else if(id.resourceType.equals("periodos"))
							id.rediretTo=vocabsHtmlBaseUrl+"rossioPeriodos/pt/";
					}
				}
			} catch (AccessException e) {
				if (e.getCause()==null && e.getCode()!=null) {
					id.httpStatusCode=Integer.parseInt(e.getCode());
				} else 
					id.httpStatusCode=500;
			}
			
			if (id.errorMessage != null) {
				if (req.getServletPath().endsWith("robots.txt") || req.getServletPath().endsWith("sitemap.xml")
						|| req.getServletPath().endsWith("favicon.ico"))
					res.sendError(404);
				else
					res.sendError(id.httpStatusCode == null ? 500 : id.httpStatusCode, id.errorMessage);
			} else if (id.httpStatusCode == null || id.httpStatusCode==200) {
				if (id.rediretTo != null)
					res.sendRedirect(id.rediretTo);
				else {
					res.setStatus(200);
					res.setContentType(id.sendDataAs.getContentType()+";UTF-8");
					rdfModel.write(res.getOutputStream(), id.sendDataAs.getName());
					rdfModel.close();
					res.flushBuffer();
				}
			} else if (id.httpStatusCode.equals(404)) {
				res.sendError(404);									
			} else if (id.httpStatusCode != null)
				throw new RuntimeException("Not implemented, sending other status codes");
			// see https://en.wikipedia.org/wiki/URL_redirection
//        		http://id.bnportugal.pt/bib/rnofa/1000
		} catch (Throwable e) {
			log.error(e.getMessage(), e);
			e.printStackTrace();
			try {
				res.sendError(500, e.getMessage());
			} catch (IOException e1) {
				// ignore
			}
		}
	}

}
