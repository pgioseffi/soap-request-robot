package br.com.pgioseffi.requisicoes.soap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.log4j.Logger;

public class ExecutaRequisicaoSOAP {

	private static final String EXTENSAO_RESPONSE = ".RESPONSE";
	private static final String EXTENSAO_DONE = ".DONE";
	private static final String EXTENSAO_DOING = ".DOING";
	private static final List<String> EXTENSOES = Arrays.asList(ExecutaRequisicaoSOAP.EXTENSAO_RESPONSE,
			ExecutaRequisicaoSOAP.EXTENSAO_DONE, ExecutaRequisicaoSOAP.EXTENSAO_DOING);

	private static final Logger LOGGER = Logger.getLogger(ExecutaRequisicaoSOAP.class);

	private static final Properties ARQUIVO_PROPERTIES = new Properties();

	static {
		ExecutaRequisicaoSOAP.LOGGER.info("Iniciando carregamento do arquivo de propriedades.");

		try (InputStream fis = ExecutaRequisicaoSOAP.class.getResourceAsStream("/configuracoes.properties")) {
			ExecutaRequisicaoSOAP.ARQUIVO_PROPERTIES.load(fis);
			ExecutaRequisicaoSOAP.LOGGER.info("Carregamento do arquivo de propriedades finalizado com sucesso.");
		} catch (final IOException e) {
			ExecutaRequisicaoSOAP.LOGGER
					.error("Erro inesperado no carregamento do arquivo de propriedades. ERRO: " + e.getMessage(), e);
			Runtime.getRuntime().exit(-1);
		}
	}

	private static final String DIRETORIO = ExecutaRequisicaoSOAP.ARQUIVO_PROPERTIES.getProperty("diretorio");
	private static final String CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO = ExecutaRequisicaoSOAP.DIRETORIO
			+ File.separatorChar
			+ ExecutaRequisicaoSOAP.ARQUIVO_PROPERTIES.getProperty("nome.arquivo.controle.execucao");

	public static void main(final String[] args) {
		ExecutaRequisicaoSOAP.LOGGER.info("Iniciando atividade de execu\u00E7\u00E3o agendada do rob\u00F3.");

		ExecutaRequisicaoSOAP.singletonJob();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				Files.delete(Paths.get(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO));
			} catch (final IOException e) {
				ExecutaRequisicaoSOAP.LOGGER
						.error("Erro ao excluir arquivo de controle de execu\u00E7\u00E3o. ERRO: " + e.getMessage(), e);
			}

			ExecutaRequisicaoSOAP.excluirArquivos();
		}));

		final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(() -> ExecutaRequisicaoSOAP.executarRequisicao(), 0, 5, TimeUnit.SECONDS);
		scheduler.scheduleAtFixedRate(() -> ExecutaRequisicaoSOAP.excluirArquivos(), 0, 1, TimeUnit.HOURS);

		ExecutaRequisicaoSOAP.LOGGER.info("Atividade agendada em execu\u00E7\u00E3o a cada cinco segundos.");
	}

	private static void singletonJob() {
		final File singleton = new File(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO);

		if (singleton.exists()) {
			ExecutaRequisicaoSOAP.LOGGER.error("Job em execu\u00E7\u00E3o.");
			Runtime.getRuntime().exit(0);
		} else {
			try (Writer writer = new BufferedWriter(
					new FileWriter(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO))) {
				writer.write("Job em execu\u00E7\u00E3o");
				writer.flush();
			} catch (final IOException e) {
				ExecutaRequisicaoSOAP.LOGGER.error(
						"Erro ao escrever arquivo de controle de execu\u00E7\u00E3o. ERRO: " + e.getMessage(), e);
				Runtime.getRuntime().exit(-1);
			}
		}
	}

	private static void executarRequisicao() {
		ExecutaRequisicaoSOAP.LOGGER.info("Entrando na rotina de execu\u00E7\u00E3o da requisi\u00E7\u00E3o SOAP em: "
				+ DateFormatUtils.format(System.currentTimeMillis(), "dd/MM/yyyy HH:mm:ss.SSS"));

		final File dir = new File(ExecutaRequisicaoSOAP.DIRETORIO);
		if (dir.exists()) {
			ExecutaRequisicaoSOAP.LOGGER.info("VERIFICANDO  DIRET\u00D3RIO: " + dir.getAbsolutePath());

			final File[] arquivos = dir.listFiles((diretorio, name) -> ExecutaRequisicaoSOAP
					.recuperarExtensaoArquivo(name).equalsIgnoreCase(".TODO"));

			ExecutaRequisicaoSOAP.LOGGER.info(
					"VERIFICANDO SE EXISTEM ARQUIVOS ELEG\u00CDVEIS PARA A ROTINA DE EXECU\u00C7\u00C3O DA REQUISI\u00C7\u00C3O SOAP.\nQuantidade de arquivo(s) para processar: "
							+ arquivos.length);

			Arrays.asList(arquivos).forEach(f -> ExecutaRequisicaoSOAP.constroiArquivosESubmeteRequisicao(f));
		}
	}

	private static void constroiArquivosESubmeteRequisicao(final File arquivo) {
		try {
			final StringBuilder corpoRequisicao = new StringBuilder();
			String configuracoes = null;
			String url = null;
			MimeHeaders mimeHeaders = null;

			final String doing = ExecutaRequisicaoSOAP.renomearArquivo(arquivo.getAbsolutePath(),
					ExecutaRequisicaoSOAP.EXTENSAO_DOING);

			try (BufferedReader reader = new BufferedReader(new FileReader(doing))) {
				configuracoes = reader.readLine();
				if (StringUtils.isBlank(configuracoes)) {
					return;
				}

				final String[] headers = configuracoes.split(";");
				if (headers.length > 1) {
					mimeHeaders = new MimeHeaders();
					url = headers[0];
					mimeHeaders.addHeader("Authorization",
							"Basic " + Base64.encodeBase64String(headers[1].getBytes(StandardCharsets.UTF_8)));
				} else {
					url = configuracoes;
				}

				String linha = null;
				while ((linha = reader.readLine()) != null) {
					corpoRequisicao.append(linha);
				}
			}

			if (corpoRequisicao.length() == 0) {
				return;
			}

			final SOAPMessage message = MessageFactory.newInstance().createMessage(mimeHeaders,
					new ByteArrayInputStream(corpoRequisicao.toString().getBytes(StandardCharsets.UTF_8)));

			final SOAPMessage response = SOAPConnectionFactory.newInstance().createConnection().call(message,
					url.trim());

			final OutputStream out = new ByteArrayOutputStream();
			response.writeTo(out);
			ExecutaRequisicaoSOAP.renomearArquivo(doing, ExecutaRequisicaoSOAP.EXTENSAO_DONE);

			try (Writer writer = new BufferedWriter(
					new FileWriter(ExecutaRequisicaoSOAP.recuperarCaminhoArquivoSemExtensao(arquivo)
							+ ExecutaRequisicaoSOAP.EXTENSAO_RESPONSE))) {
				writer.write(out.toString());
				writer.flush();
			}
		} catch (final IOException | SOAPException e) {
			ExecutaRequisicaoSOAP.LOGGER
					.error("Erro inesperado ao executar requisiu\u00E7\u00E3o SOAP. ERRO: " + e.getMessage(), e);
		}
	}

	private static String renomearArquivo(final String caminhoAbsolutoArquivo, final String extensaoNova)
			throws IOException {
		final Path origem = Paths.get(caminhoAbsolutoArquivo);
		final Path retorno = Files.move(origem, origem.resolveSibling(
				ExecutaRequisicaoSOAP.recuperarCaminhoArquivoSemExtensao(caminhoAbsolutoArquivo) + extensaoNova),
				StandardCopyOption.REPLACE_EXISTING);

		return retorno.toString();
	}

	private static void excluirArquivos() {
		final File diretorio = new File(ExecutaRequisicaoSOAP.DIRETORIO);

		if (diretorio.exists()) {
			Arrays.asList(diretorio.listFiles((file) -> {
				final String nomeArquivo = file.getName();
				return ExecutaRequisicaoSOAP.EXTENSOES
						.contains(ExecutaRequisicaoSOAP.recuperarExtensaoArquivo(nomeArquivo))
						&& System.currentTimeMillis() - file.lastModified() >= 3600000;
			})).forEach(File::delete);
		}
	}

	private static String recuperarCaminhoArquivoSemExtensao(final File arquivo) {
		return ExecutaRequisicaoSOAP.recuperarCaminhoArquivoSemExtensao(arquivo.getAbsolutePath());
	}

	private static String recuperarCaminhoArquivoSemExtensao(final String caminhoAbsolutoArquivo) {
		return caminhoAbsolutoArquivo.substring(0, caminhoAbsolutoArquivo.lastIndexOf('.'));
	}

	private static String recuperarExtensaoArquivo(final String nomeArquivo) {
		return nomeArquivo.substring(nomeArquivo.lastIndexOf('.'));
	}
}