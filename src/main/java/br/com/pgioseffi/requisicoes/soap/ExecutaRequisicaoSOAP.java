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
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

/**
 * <p>
 * Classe respons&aacute;vel por executar requisi&ccedil;&otilde;es SOAP a cada
 * cinco segundos atrav&eacute;s da leitura de um arquivo
 * {@link ExecutaRequisicaoSOAP#EXTENSAO_PENDING PENDING} com conte&uacute;do
 * XML com um envelope SOAP v&aacute;lido e manter por uma hora a resposta desta
 * mesma requisi&ccedil;&atilde;o em um arquivo
 * {@link ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE RESPONSE}.
 * </p>
 * <p>
 * N&atilde;o &eacute; responsabilidade desta classe validar o envelope SOAP no
 * arquivo XML. Esta valida&ccedil;&atilde;o ser&aacute; feita em outra classe e
 * utilizada aqui antes da chamada para a requisi&ccedil;&atilde;o.
 * </p>
 * <p>
 * Para evitar requisi&ccedil;&otilde;s repetidas, esta classe manipula os
 * arquivos modificando suas extens&otilde;es conforme abaixo, os utilizando
 * como &quot;status&quot:
 * <ul>
 * <li>{@link ExecutaRequisicaoSOAP#EXTENSAO_PENDING PENDING}: Arquivos com
 * requisi&ccedil;&otilde;es pendentes;</li>
 * <li>{@link ExecutaRequisicaoSOAP#EXTENSAO_DOING DOING}: Arquivos com
 * requisi&ccedil;&otilde;es em andamento;</li>
 * <li>{@link ExecutaRequisicaoSOAP#EXTENSAO_DONE DONE}: Arquivos originais com
 * requisi&ccedil;&otilde;es realizadas;</li>
 * <li>{@link ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE RESPONSE}: Arquivos de
 * resposta das requisi&ccedil;&otilde;es realizadas;</li>
 * </ul>
 * </p>
 * <p>
 * Esta classe tem como premissa iniciar suas constantes atrav&eacute;s de um
 * {@link Properties arquivo de propriedades} para localizar qual
 * diret&oacute;rio utilizar para buscar os arquivos com as
 * requisi&ccedil;&otilde;es SOAP. No caso de falha do carregamento deste
 * arquivo, esta classe aborta sua execu&ccedil;&atilde;o.
 * </p>
 * <p>
 * Esta classe foi testada apenas em ambientes
 * <code><strong>Windows</strong></code>, por&eacute;m utiliza o novo pacote
 * {@link java.nio} que cont&eacute;m diversas melhorias no que diz respeito
 * &agrave; sistemas operacionais.
 * </p>
 *
 * @author <a href="mailto:pgioseffi@gmail.com">Philippe Gioseffi
 *         &lt;pgioseffi@gmail.com&gt;</a>
 *
 * @since 1.0.0
 *
 * @see Thread
 * @see Runnable
 * @see ScheduledExecutorService
 * @see SOAPMessage
 * @see MimeHeaders
 * @see Properties
 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING
 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE
 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE EXTENSAO_RESPONSE
 */
public class ExecutaRequisicaoSOAP {

	/**
	 * <p>
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>PENDING</code>.
	 * </p>
	 * <p>
	 * Esta &eacute; a &uacute;nica constante pela qual o rob&ocirc; n&atilde;o
	 * &eacute; diretamente respons&aacute;vel, por isso n&atilde;o deve ser
	 * inserida na lista de {@linkplain ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES}.
	 * </p>
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES
	 */
	private static final String EXTENSAO_PENDING = ".PENDING";

	/**
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>RESPONSE</code>.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE
	 * @see ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES
	 */
	private static final String EXTENSAO_RESPONSE = ".RESPONSE";

	/**
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>DONE</code>.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE EXTENSAO_RESPONSE
	 * @see ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES
	 */
	private static final String EXTENSAO_DONE = ".DONE";

	/**
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>DOING</code>.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE EXTENSAO_RESPONSE
	 * @see ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES
	 */
	private static final String EXTENSAO_DOING = ".DOING";

	/**
	 * <p>
	 * Constante utilizada para a manipula&ccedil;&atilde;o das extens&otilde;es que
	 * servem como &quot;status&quot deste rob&ocirc; atrav&eacute;s das constantes
	 * {@link ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE},
	 * {@link ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING} e
	 * {@link ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE EXTENSAO_RESPONSE}.
	 * </p>
	 * <p>
	 * Tais extens&otilde;es mencionadas acima n&atilde;o contemplam a constante
	 * {@link ExecutaRequisicaoSOAP#EXTENSAO_PENDING EXTENSAO_PENDING} pelo motivo
	 * explicitado no Javadoc da pr&oacute;pria constante.
	 * </p>
	 * <p>
	 * Esta constante foi criada utilizando
	 * {@link Collections#unmodifiableCollection(Collection)} de maneira que
	 * n&atilde;o possa ser modificada em tempo de execu&ccedil;&atilde;o.
	 * </p>
	 *
	 * @see Arrays#asList(Object...)
	 * @see Collections#unmodifiableCollectionCollection)
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_PENDING EXTENSAO_PENDING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE EXTENSAO_RESPONSE
	 */
	private static final Collection<String> EXTENSOES = Collections
			.unmodifiableList(Arrays.asList(ExecutaRequisicaoSOAP.EXTENSAO_RESPONSE,
					ExecutaRequisicaoSOAP.EXTENSAO_DONE, ExecutaRequisicaoSOAP.EXTENSAO_DOING));

	/**
	 * Constante utilizada para manter o {@link Logger log} da classe.
	 *
	 * @see Logger
	 */
	private static final Logger LOGGER = Logger.getLogger(ExecutaRequisicaoSOAP.class);

	/**
	 * Constante utilizada para manter o {@link Properties arquivo de propriedades}
	 * da classe.
	 *
	 * @see Properties
	 * @see ExecutaRequisicaoSOAP#DIRETORIO DIRETORIO
	 * @see ExecutaRequisicaoSOAP#CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO
	 *      CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO
	 */
	private static final Properties ARQUIVO_PROPERTIES = new Properties();

	static {
		// Os logs já dizem exatamente o que está sendo feito, sem necessidade de
		// comentários então.
		ExecutaRequisicaoSOAP.LOGGER.info("Iniciando carregamento do arquivo de propriedades.");

		try (InputStream fis = ExecutaRequisicaoSOAP.class.getResourceAsStream("/configuracoes.properties")) {
			ExecutaRequisicaoSOAP.ARQUIVO_PROPERTIES.load(fis);
			ExecutaRequisicaoSOAP.LOGGER.info("Carregamento do arquivo de propriedades finalizado com sucesso.");
		} catch (final IOException e) {
			ExecutaRequisicaoSOAP.LOGGER
					.error("Erro inesperado no carregamento do arquivo de propriedades. ERRO: " + e.getMessage(), e);

			// Sai da execução sinalizando erro.
			Runtime.getRuntime().exit(-1);
		}
	}

	/**
	 * Constante utilizada para manter o diret&oacute;rio a ser varrido em busca de
	 * arquivos do tipo {@link ExecutaRequisicaoSOAP#EXTENSAO_PENDING PENDING} com
	 * as requisi&ccedil;&otilde;es a serem feitas em formato de envelope SOAP.
	 *
	 * @see Properties
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_PENDING EXTENSAO_PENDING
	 * @see ExecutaRequisicaoSOAP#ARQUIVO_PROPERTIES ARQUIVO_PROPERTIES
	 */
	private static final String DIRETORIO = ExecutaRequisicaoSOAP.ARQUIVO_PROPERTIES.getProperty("diretorio");

	/**
	 * Constante utilizada para manter o nome do arquivo que controla se o job
	 * j&aacute; est&aacute; em execu&ccedil;&atilde;o ou n&atilde;o por um
	 * determinado usu&aacute;rio.
	 *
	 * @see Properties
	 * @see ExecutaRequisicaoSOAP#ARQUIVO_PROPERTIES ARQUIVO_PROPERTIES
	 */
	private static final String CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO = ExecutaRequisicaoSOAP.DIRETORIO
			+ File.separatorChar
			+ ExecutaRequisicaoSOAP.ARQUIVO_PROPERTIES.getProperty("nome.arquivo.controle.execucao");

	private ExecutaRequisicaoSOAP() {
		super();
	}

	/**
	 * M&eacute;todo de entrada do rob&ocirc;. Este m&eacute;todo inicia a
	 * execu&ccedil;&atilde;o do rob&ocirc; para que o mesmo realize as
	 * requisi&ccedil;&otilde;es SOAP.
	 *
	 * @param args
	 *            Par&acirc;metro obrigat&oacute;rio segundo
	 *            especifica&ccedil;&atilde;o Java para classes que sejam iniciadas
	 *            com m&eacute;todos <code>main</code>.
	 *
	 * @see ExecutaRequisicaoSOAP#executarRequisicao() executarRequisicao()
	 * @see ExecutaRequisicaoSOAP#excluirArquivos() excluirArquivos()
	 * @see ExecutaRequisicaoSOAP#singletonJob() singletonJob()
	 */
	public static void main(final String[] args) {
		ExecutaRequisicaoSOAP.LOGGER.info("Iniciando atividade de execu\u00E7\u00E3o agendada do rob\u00F3.");

		// Chama o método que garante que este robô será executado apenas por um
		// usuário.
		ExecutaRequisicaoSOAP.singletonJob();

		// Instrução para a JVM realizar os passos abaixo de exclusão de arquivos ao fim
		// da execução do job.
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				Files.delete(Paths.get(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO));
			} catch (final IOException e) {
				ExecutaRequisicaoSOAP.LOGGER
						.error("Erro ao excluir arquivo de controle de execu\u00E7\u00E3o. ERRO: " + e.getMessage(), e);
			}

			// Excluir arquivos do tipo done, response e doing (este caso não deve ocorrer)
			// do diretório.
			ExecutaRequisicaoSOAP.excluirArquivos();
		}));

		// Scheduler responsável por iniciar os agendamentos para as requisições SOAP e
		// exclusões de arquivos de hora em hora.
		final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(() -> ExecutaRequisicaoSOAP.executarRequisicao(), 0, 5, TimeUnit.SECONDS);
		scheduler.scheduleAtFixedRate(() -> ExecutaRequisicaoSOAP.excluirArquivos(), 0, 1, TimeUnit.HOURS);

		ExecutaRequisicaoSOAP.LOGGER.info("Atividade agendada em execu\u00E7\u00E3o a cada cinco segundos.");
	}

	/**
	 * M&eacute;todo respons&aacute;vel por garantir que este rob&ocirc; seja
	 * executado apenas uma vez por usu&aacute;rio atrav&eacute;s de um arquivo de
	 * controle.
	 */
	private static void singletonJob() {
		final String mensagem = "Job em execu\u00E7\u00E3o.";

		// Iniciando arquivo de controle.
		final File singleton = new File(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO);

		if (singleton.exists()) {
			// Se o mesmo já existir, o robô já está sendo executado por um usuário. Log e
			// saia.
			ExecutaRequisicaoSOAP.LOGGER.error(mensagem);

			// Sai da execução sinalizando condição aceitável.
			Runtime.getRuntime().exit(0);
		} else {
			// Senão estiver sendo executado, crie um arquivo com uma mensagem padrão.
			try (Writer writer = new BufferedWriter(
					new FileWriter(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO))) {
				writer.write(mensagem);
				writer.flush();
			} catch (final IOException e) {
				ExecutaRequisicaoSOAP.LOGGER.error(
						"Erro ao escrever arquivo de controle de execu\u00E7\u00E3o. ERRO: " + e.getMessage(), e);

				// Sai da execução sinalizando erro.
				Runtime.getRuntime().exit(-1);
			}
		}
	}

	/**
	 * M&eacute;todo respons&aacute;vel por varrer o diret&oacute;rio em busca de
	 * arquivos do tipo {@link ExecutaRequisicaoSOAP#EXTENSAO_PENDING PENDING} e
	 * executar a requisi&ccedil;&atilde;o.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_PENDING EXTENSAO_PENDING
	 * @see ExecutaRequisicaoSOAP#construirArquivosESubmeterRequisicao(File)
	 *      construirArquivosESubmeteRequisicao(File)
	 * @see File
	 * @see File#getName()
	 * @see File#isFile()
	 * @see File#listFiles(java.io.FileFilter) File#listFiles(FileFilter)
	 */
	private static void executarRequisicao() {
		ExecutaRequisicaoSOAP.LOGGER.info("Entrando na rotina de execu\u00E7\u00E3o da requisi\u00E7\u00E3o SOAP em: "
				+ DateFormatUtils.format(System.currentTimeMillis(), "dd/MM/yyyy HH:mm:ss.SSS"));

		// Instanciando objeto file com caminho do diretório que será utilizado para
		// varremos em busca de arquivos ".PENDING" com as requisições SOAP.
		final File dir = new File(ExecutaRequisicaoSOAP.DIRETORIO);
		if (dir.exists()) {
			ExecutaRequisicaoSOAP.LOGGER.info("VERIFICANDO  DIRET\u00D3RIO: " + dir.getAbsolutePath());

			// Buscando no diretório apenas arquivos do tipo ".PENDING" e que sejam arquivos
			// e não diretórios.
			final File[] arquivos = dir.listFiles(file -> file.isFile() && ExecutaRequisicaoSOAP
					.recuperarExtensaoArquivo(file.getName()).equalsIgnoreCase(ExecutaRequisicaoSOAP.EXTENSAO_PENDING));

			ExecutaRequisicaoSOAP.LOGGER.info(
					"VERIFICANDO SE EXISTEM ARQUIVOS ELEG\u00CDVEIS PARA A ROTINA DE EXECU\u00C7\u00C3O DA REQUISI\u00C7\u00C3O SOAP.\nQuantidade de arquivo(s) para processar: "
							+ arquivos.length);

			// Varre a lista de arquivos encontrados e submete a requisição.
			Arrays.asList(arquivos).forEach(f -> ExecutaRequisicaoSOAP.construirArquivosESubmeterRequisicao(f));
		}
	}

	/**
	 * M&eacute;todo respons&aacute;vel por submeter cada requisi&ccedil;&atilde;o
	 * que chega por arquivo.
	 *
	 * @param arquivo
	 *            {@link File Arquivo} que ser&aacute; &quot;parseado&quot; com a
	 *            requisi&ccedil;&atilde;o SOAP e, caso necess&aacute;rio, login e
	 *            senha na primeira linha junto ao endere&ccedil;o da
	 *            requisi&ccedil;&atilde;o.
	 *
	 * @see Writer
	 * @see OutputStream
	 * @see SOAPMessage
	 */
	private static void construirArquivosESubmeterRequisicao(final File arquivo) {
		try {
			final StringBuilder corpoRequisicao = new StringBuilder();
			String configuracoes = null;
			String url = null;
			MimeHeaders mimeHeaders = null;

			// Como dito no javadoc da classe, a extensão do arquivo é utilizada como
			// status, então para evitarmos repetições com robôs de outros usuários, mudamos
			// a extensão para doing.
			final String doing = ExecutaRequisicaoSOAP.renomearArquivo(arquivo, ExecutaRequisicaoSOAP.EXTENSAO_DOING);

			try (BufferedReader reader = new BufferedReader(new FileReader(doing))) {
				// Leitura do arquivo. A primeira linha contém as "configurações" do mesmo.
				configuracoes = reader.readLine();

				// Senão tivermos configurações o arquivo é inválido. Devemos avisar e seguir
				// para o próximo.
				if (StringUtils.isBlank(configuracoes)) {
					ExecutaRequisicaoSOAP.LOGGER.error(
							"Arquivo inv\u00E1lido, pois n\u00E3o cont\u00E9m as configura\u00E7\u00F5es da requisi\u00E7\u00E3o SOAP.");
					return;
				}

				// Caso tenhamos ";" na linha, sabemos que temos um job com necessidade de
				// utilização de autenticação com Basic Authentication.
				final String[] headers = configuracoes.split(";");
				if (headers.length > 1) {
					mimeHeaders = new MimeHeaders();
					url = headers[0];
					mimeHeaders.addHeader("Authorization",
							"Basic " + Base64.encodeBase64String(headers[1].getBytes(StandardCharsets.UTF_8)));
				} else {
					// Caso contrário temos só a URL mesmo.
					url = configuracoes;
				}

				String linha = null;
				while ((linha = reader.readLine()) != null) {
					corpoRequisicao.append(linha);
				}
			}

			// Senão tivermos corpo da requisição o arquivo é inválido. Devemos avisar e
			// seguir para o próximo.
			if (corpoRequisicao.length() == 0) {
				ExecutaRequisicaoSOAP.LOGGER.error(
						"Arquivo inv\u00E1lido, pois n\u00E3o cont\u00E9m o corpo (envelope SOAP) da requisi\u00E7\u00E3o SOAP.");
				return;
			}

			// Cria o objeto com a mensagem SOAP a ser enviada.
			final SOAPMessage message = MessageFactory.newInstance().createMessage(mimeHeaders,
					new ByteArrayInputStream(corpoRequisicao.toString().getBytes(StandardCharsets.UTF_8)));

			// Recupera a resposta depois de executada a requisição com a mensagem SOAP
			// acima.
			final SOAPMessage response = SOAPConnectionFactory.newInstance().createConnection().call(message,
					url.trim());

			// TODO: Verificar possibilidade de utilizar FileOutputStream e eliminar
			// overhead no filewriter abaixo.
			// Escreve a resposta num stream.
			final OutputStream out = new ByteArrayOutputStream();
			response.writeTo(out);

			// Renomeia arquivo de entrada para constar como feito através da extensão DONE.
			ExecutaRequisicaoSOAP.renomearArquivo(doing, ExecutaRequisicaoSOAP.EXTENSAO_DONE);

			// Escreve arquivo de resposta do tipo RESPONSE.
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

	/**
	 * <p>
	 * Sobrecarga de m&eacute;todo para o m&eacute;todo
	 * {@link ExecutaRequisicaoSOAP#renomearArquivo(String, String)
	 * renomearArquivo(String, String)} que efetivamente faz o que tem que ser
	 * feito.
	 * </p>
	 * <p>
	 * Este m&eacute;todo apenas chama o j&aacute; mencionado m&eacute;todo acima
	 * passando os par&acirc;metros documentados.
	 * </p>
	 *
	 * @param arquivo
	 *            Objeto do tipo {@link File} que passar&aacute; para o
	 *            m&eacute;todo
	 *            {@link ExecutaRequisicaoSOAP#renomearArquivo(String, String)
	 *            renomearArquivo(String, String)} o
	 *            {@linkplain File#getAbsolutePath() caminho absoluto} do arquivo.
	 * @param extensaoNova
	 *            Variável do tipo {@link String} contendo uma das extens&otilde;es
	 *            aceitas pelo rob&ocirc;.
	 *
	 * @return Objeto do tipo {@link String} contendo o caminho absoluto do arquivo
	 *         renomeado.
	 *
	 * @throws IOException
	 *             Esta exce&ccedil;&atilde;o &eacute; lan&ccedil;ada por um dos
	 *             dois motivos:
	 *             <ul>
	 *             <li>Pelo m&eacute;todo
	 *             {@link Files#move(Path, Path, java.nio.file.CopyOption...)
	 *             Files#move(Path, Path, CopyOption...)} e relan&ccedil;ada por
	 *             este m&eacute;todo para que seja tratado por quem o chamou;</li>
	 *             <li>Caso seja passado no par&acirc;metro
	 *             <code><strong>extensaoNova</strong></code> um valor que
	 *             n&atilde;o esteja definido na constante
	 *             {@link ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES}.</li>
	 *             </ul>
	 * @throws SecurityException
	 *             Lan&ccedil;ada pelo m&eacute;todo
	 *             {@link Files#move(Path, Path, java.nio.file.CopyOption...)
	 *             Files#move(Path, Path, CopyOption...)} e relan&ccedil;ada por
	 *             este m&eacute;todo para que seja tratado por quem o chamou.
	 * @throws UnsupportedOperationException
	 *             Lan&ccedil;ada pelo m&eacute;todo
	 *             {@link Files#move(Path, Path, java.nio.file.CopyOption...)
	 *             Files#move(Path, Path, CopyOption...)} e relan&ccedil;ada por
	 *             este m&eacute;todo para que seja tratado por quem o chamou.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES
	 * @see ExecutaRequisicaoSOAP#renomearArquivo(String, String)
	 *      renomearArquivo(String, String)
	 * @see String
	 * @see Path
	 * @see java.nio.file.CopyOption CopyOption
	 * @see StandardCopyOption
	 * @see Files#move(Path, Path, java.nio.file.CopyOption...) Files#move(Path,
	 *      Path, CopyOption...)
	 */
	private static String renomearArquivo(final File arquivo, final String extensaoNova) throws IOException {
		return ExecutaRequisicaoSOAP.renomearArquivo(arquivo.getAbsolutePath(), extensaoNova);
	}

	/**
	 * M&eacute;todo respons&aacute;vel por renomear um arquivo utilizado para uma
	 * das extens&otilde;es reconhecidas por este rob&ocirc;.
	 *
	 * @param caminhoAbsolutoArquivo
	 *            Variável do tipo {@link String} contendo o caminho absoluto do
	 *            arquivo a ser renomeado.
	 * @param extensaoNova
	 *            Variável do tipo {@link String} contendo uma das extens&otilde;es
	 *            aceitas pelo rob&ocirc;.
	 *
	 * @return Objeto do tipo {@link String} contendo o caminho absoluto do arquivo
	 *         renomeado.
	 *
	 * @throws IOException
	 *             Esta exce&ccedil;&atilde;o &eacute; lan&ccedil;ada por um dos
	 *             dois motivos:
	 *             <ul>
	 *             <li>Pelo m&eacute;todo
	 *             {@link Files#move(Path, Path, java.nio.file.CopyOption...)
	 *             Files#move(Path, Path, CopyOption...)} e relan&ccedil;ada por
	 *             este m&eacute;todo para que seja tratado por quem o chamou;</li>
	 *             <li>Caso seja passado no par&acirc;metro
	 *             <code><strong>extensaoNova</strong></code> um valor que
	 *             n&atilde;o esteja definido na constante
	 *             {@link ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES}.</li>
	 *             </ul>
	 * @throws SecurityException
	 *             Lan&ccedil;ada pelo m&eacute;todo
	 *             {@link Files#move(Path, Path, java.nio.file.CopyOption...)
	 *             Files#move(Path, Path, CopyOption...)} e relan&ccedil;ada por
	 *             este m&eacute;todo para que seja tratado por quem o chamou.
	 * @throws UnsupportedOperationException
	 *             Lan&ccedil;ada pelo m&eacute;todo
	 *             {@link Files#move(Path, Path, java.nio.file.CopyOption...)
	 *             Files#move(Path, Path, CopyOption...)} e relan&ccedil;ada por
	 *             este m&eacute;todo para que seja tratado por quem o chamou.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES
	 * @see ExecutaRequisicaoSOAP#recuperarCaminhoArquivoSemExtensao(String)
	 *      recuperarCaminhoArquivoSemExtensao(String)
	 * @see ExecutaRequisicaoSOAP#renomearArquivo(File, String)
	 *      renomearArquivo(File, String)
	 * @see String
	 * @see Path
	 * @see java.nio.file.CopyOption CopyOption
	 * @see StandardCopyOption
	 * @see Files#move(Path, Path, CopyOption...) Files#move(Path, Path,
	 *      CopyOption...)
	 */
	private static String renomearArquivo(final String caminhoAbsolutoArquivo, final String extensaoNova)
			throws IOException {
		if (!ExecutaRequisicaoSOAP.EXTENSOES.contains(extensaoNova)) {
			throw new IOException(
					"Extens\u00E3o inv\u00E1lida passada por par\u00E2metro para o m\u00E9todo ExecutaRequisicaoSOAP#renomearArquivo(String, String).");
		}

		// Recuperando caminho do arquivo a ser renomeado.
		final Path origem = Paths.get(caminhoAbsolutoArquivo);

		// Renomeando (mesmo com o nome do método pela API Java sendo estranho).
		final Path retorno = Files.move(origem, origem.resolveSibling(
				ExecutaRequisicaoSOAP.recuperarCaminhoArquivoSemExtensao(caminhoAbsolutoArquivo) + extensaoNova),
				StandardCopyOption.REPLACE_EXISTING);

		// Retornando caminho absoluto do arquivo renomeado.
		return retorno.toString();
	}

	/**
	 * M&eacute;todo respons&aacute;vel por excluir os arquivos dos tipos definidos
	 * pela constante {@link ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES} no
	 * diret&oacute;rio definido pela constante
	 * {@link ExecutaRequisicaoSOAP#DIRETORIO DIRETORIO} que tenham sido criado
	 * h&aacute; mais de uma hora.
	 *
	 * @see ExecutaRequisicaoSOAP#recuperarExtensaoArquivo(String)
	 *      recuperarExtensaoArquivo(String)
	 * @see ExecutaRequisicaoSOAP#DIRETORIO DIRETORIO
	 * @see ExecutaRequisicaoSOAP#EXTENSOES EXTENSOES
	 * @see File
	 * @see File#lastModified()
	 * @see File#delete()
	 * @see File#getName()
	 * @see File#isFile()
	 * @see File#listFiles(java.io.FileFilter) File#listFiles(FileFilter)
	 * @see System#currentTimeMillis()
	 */
	private static void excluirArquivos() {
		final File diretorio = new File(ExecutaRequisicaoSOAP.DIRETORIO);

		if (diretorio.exists()) {
			// Se o diretório existir, recupero os arquivos do tipo definido pela constante
			// criado há mais de uma hora, transformo numa lista e excluo.
			Arrays.asList(diretorio.listFiles((file) -> ExecutaRequisicaoSOAP.EXTENSOES
					.contains(ExecutaRequisicaoSOAP.recuperarExtensaoArquivo(file.getName())) && file.isFile()
					&& System.currentTimeMillis() - file.lastModified() >= 3600000)).forEach(File::delete);
		}
	}

	/**
	 * <p>
	 * Sobrecarga de m&eacute;todo para o m&eacute;todo
	 * {@link ExecutaRequisicaoSOAP#recuperarCaminhoArquivoSemExtensao(String)
	 * recuperarCaminhoArquivoSemExtensao(String)} que efetivamente faz o que tem
	 * que ser feito.
	 * </p>
	 * <p>
	 * Este m&eacute;todo apenas chama o j&aacute; mencionado m&eacute;todo acima
	 * passando a {@link String} retornada pelo m&eacute;todo
	 * {@linkplain File#getAbsolutePath()} contendo o caminho absoluto do arquivo,
	 * atrav&eacute;s do par&acirc;metro documentado.
	 * </p>
	 *
	 * @param arquivo
	 *            Objeto do tipo {@link File} que passar&aacute; para o
	 *            m&eacute;todo
	 *            {@link ExecutaRequisicaoSOAP#recuperarCaminhoArquivoSemExtensao(String)
	 *            recuperarCaminhoArquivoSemExtensao(String)} o
	 *            {@linkplain File#getAbsolutePath() caminho absoluto} do arquivo.
	 *
	 * @return Objeto do tipo {@link String} contendo o caminho absoluto do
	 *         {@link File arquivo} sem a sua extens&atilde;o.
	 *
	 * @see ExecutaRequisicaoSOAP#recuperarCaminhoArquivoSemExtensao(String)
	 * @see String
	 * @see File#getAbsolutePath()
	 */
	private static String recuperarCaminhoArquivoSemExtensao(final File arquivo) {
		return ExecutaRequisicaoSOAP.recuperarCaminhoArquivoSemExtensao(arquivo.getAbsolutePath());
	}

	/**
	 * M&eacute;todo respons&aacute;vel por dado o caminho absoluto de um arquivo,
	 * retornar o mesmo sem a extens&atilde;o do mesmo.
	 *
	 * @param arquivo
	 *            Objeto do tipo {@link String} contendo o caminho absoluto de um
	 *            arquivo.
	 *
	 * @return Objeto do tipo {@link String} contendo o caminho absoluto do
	 *         {@link File arquivo} sem a sua extens&atilde;o.
	 *
	 * @see ExecutaRequisicaoSOAP#recuperarCaminhoArquivoSemExtensao(File)
	 * @see String
	 * @see String#lastIndexOf(int)
	 * @see String#substring(int, int)
	 */
	private static String recuperarCaminhoArquivoSemExtensao(final String caminhoAbsolutoArquivo) {
		// Retorna o caminho absoluto sem a extensão fazendo uma busca do início da
		// String até a posição do último ponto encontrado.
		return caminhoAbsolutoArquivo.substring(0, caminhoAbsolutoArquivo.lastIndexOf('.'));
	}

	/**
	 * M&eacute;todo respons&aacute;vel por dado o caminho absoluto de um arquivo,
	 * retornar sua extens&atilde;o com o s&iacute;mbolo de
	 * &quot;<code><strong>.</strong></code>&quot;.
	 *
	 * @param arquivo
	 *            Objeto do tipo {@link String} contendo o caminho absoluto de um
	 *            arquivo.
	 *
	 * @return Objeto do tipo {@link String} contendo apenas a extens&atilde;o deste
	 *         mesmo arquivo com o s&iacute;mbolo de
	 *         &quot;<code><strong>.</strong></code>&quot;.
	 *
	 * @see String
	 * @see String#lastIndexOf(int)
	 * @see String#substring(int, int)
	 */
	private static String recuperarExtensaoArquivo(final String nomeArquivo) {
		return nomeArquivo.substring(nomeArquivo.lastIndexOf('.'));
	}
}