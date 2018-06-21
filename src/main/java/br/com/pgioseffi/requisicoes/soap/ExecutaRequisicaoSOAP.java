package br.com.pgioseffi.requisicoes.soap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * <code><strong>Windows</strong></code>, por&eacute;m utiliza, em sua maioria
 * de c&oacute;digo, o novo pacote {@link java.nio} que cont&eacute;m diversas
 * melhorias no que diz respeito &agrave; independ&ecirc;ncia de plataformas.
 * </p>
 *
 * @author <a href="mailto:pgioseffi@gmail.com">Philippe Gioseffi
 *         &lt;pgioseffi@gmail.com&gt;</a>
 *
 * @since 1.0.0
 *
 * @see ExecutaRequisicaoSOAP#EXTENSAO_PENDING EXTENSAO_PENDING
 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING
 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE
 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE EXTENSAO_RESPONSE
 * @see Runtime
 * @see Runtime#getRuntime()
 * @see Runtime#addShutdownHook(Thread)
 * @see Thread
 * @see Runnable
 * @see ScheduledExecutorService
 * @see SOAPMessage
 * @see MimeHeaders
 * @see Properties
 * @see Files
 * @see Files#list(Path)
 * @see Files#isRegularFile(Path, java.nio.file.LinkOption...)
 *      Files.isRegularFile(Path, LinkOption...)
 * @see Files#newBufferedReader(Path, java.nio.charset.Charset)
 *      Files.newBufferedReader(Path, Charset)
 * @see Path
 * @see Stream
 * @see Stream#collect(java.util.stream.Collector) Stream.collect(Collector)
 * @see Collectors
 * @see Collectors#toCollection(java.util.function.Supplier)
 *      Collectors.toCollection(Supplier)
 */
public class ExecutaRequisicaoSOAP {

	/**
	 * <p>
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>PENDING</code>.
	 * </p>
	 * <p>
	 * Esta &eacute; a &uacute;nica constante pela qual o rob&ocirc; n&atilde;o
	 * &eacute; diretamente respons&aacute;vel.
	 * </p>
	 */
	private static final String EXTENSAO_PENDING = ".PENDING";

	/**
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>RESPONSE</code>.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE
	 */
	private static final String EXTENSAO_RESPONSE = ".RESPONSE";

	/**
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>DONE</code>.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE EXTENSAO_RESPONSE
	 */
	private static final String EXTENSAO_DONE = ".DONE";

	/**
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>DOING</code>.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE EXTENSAO_RESPONSE
	 */
	private static final String EXTENSAO_DOING = ".DOING";

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
			ExecutaRequisicaoSOAP.LOGGER.error("Erro inesperado no carregamento do arquivo de propriedades. ERRO: " + e.getMessage(), e);

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
	private static final Path DIRETORIO = Paths.get(ExecutaRequisicaoSOAP.ARQUIVO_PROPERTIES.getProperty("diretorio"));

	/**
	 * Constante utilizada para manter o nome do arquivo que controla se o job
	 * j&aacute; est&aacute; em execu&ccedil;&atilde;o ou n&atilde;o por um
	 * determinado usu&aacute;rio.
	 *
	 * @see Properties
	 * @see Path
	 * @see ExecutaRequisicaoSOAP#DIRETORIO DIRETORIO
	 * @see ExecutaRequisicaoSOAP#ARQUIVO_PROPERTIES ARQUIVO_PROPERTIES
	 */
	private static final Path CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO = ExecutaRequisicaoSOAP.DIRETORIO
			.resolve(ExecutaRequisicaoSOAP.ARQUIVO_PROPERTIES.getProperty("nome.arquivo.controle.execucao"));

	private static final FileLock LOCK = ExecutaRequisicaoSOAP.adquirirBloqueioArquivoControleSegundaFase();

	private static RandomAccessFile adquirirBloqueioArquivoControlePrimeiraFase() throws IOException {
		final String mensagem = "Job em execu\u00E7\u00E3o pelo usu\u00E1rio "
				+ ExecutaRequisicaoSOAP.substring(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO).toUpperCase(Locale.getDefault());

		if (Files.exists(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO)) {
			// Se o mesmo já existir, o robô já está sendo executado por um usuário. Log e
			// saia.
			ExecutaRequisicaoSOAP.LOGGER.error(mensagem);

			// Sai da execução sinalizando condição aceitável.
			Runtime.getRuntime().exit(0);
		}

		if (!Files.exists(ExecutaRequisicaoSOAP.DIRETORIO)) {
			Files.createDirectory(ExecutaRequisicaoSOAP.DIRETORIO);
		}

		return new RandomAccessFile(
				Files.write(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO, mensagem.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE).toFile(),
				"rw");
	}

	private static FileLock adquirirBloqueioArquivoControleSegundaFase() {
		try {
			return ExecutaRequisicaoSOAP.adquirirBloqueioArquivoControlePrimeiraFase().getChannel().lock(0, Long.MAX_VALUE, false);
		} catch (final Throwable e) {
			ExecutaRequisicaoSOAP.LOGGER.error("Erro ao escrever arquivo de controle de execu\u00E7\u00E3o. ERRO: " + e.getMessage(), e);
			System.exit(-1);
			return null;
		}
	}

	/**
	 * Construtor padr&atilde;o de maneira a evitar instancia&ccedil;&atilde;o da
	 * classe.
	 */
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
	 * @see Runtime
	 * @see Runtime#getRuntime()
	 * @see Runtime#addShutdownHook(Thread)
	 */
	public static void main(final String[] args) {
		ExecutaRequisicaoSOAP.LOGGER.info("Iniciando atividade de execu\u00E7\u00E3o agendada do rob\u00F4.");

		// Instrução para a JVM realizar os passos abaixo de exclusão de arquivos ao fim
		// da execução do job.
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {

			// Excluir arquivos do tipo done, response e doing (este caso não deve ocorrer)
			// do diretório.
			ExecutaRequisicaoSOAP.excluirArquivos();

			try {
				ExecutaRequisicaoSOAP.LOCK.release();

				Files.delete(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO);

				// Se for a última instância do robô a utilizar o diretório, posso apagá-lo.
				if (Files.list(ExecutaRequisicaoSOAP.DIRETORIO).count() == 0L) {
					Files.delete(ExecutaRequisicaoSOAP.DIRETORIO);
				}
			} catch (final IOException e) {
				ExecutaRequisicaoSOAP.LOGGER.error("Erro ao excluir arquivo de controle de execu\u00E7\u00E3o. ERRO: " + e.getMessage(), e);
			}
		}));

		// Scheduler responsável por iniciar os agendamentos para as requisições SOAP e
		// exclusões de arquivos de hora em hora.
		final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(() -> ExecutaRequisicaoSOAP.executarRequisicao(), 0, 5, TimeUnit.SECONDS);
		scheduler.scheduleAtFixedRate(() -> ExecutaRequisicaoSOAP.excluirArquivos(), 0, 1, TimeUnit.HOURS);

		ExecutaRequisicaoSOAP.LOGGER.info("Atividade agendada em execu\u00E7\u00E3o a cada cinco segundos.");
	}

	/**
	 * M&eacute;todo respons&aacute;vel por varrer o diret&oacute;rio em busca de
	 * arquivos do tipo {@link ExecutaRequisicaoSOAP#EXTENSAO_PENDING PENDING} e
	 * executar a requisi&ccedil;&atilde;o.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_PENDING EXTENSAO_PENDING
	 * @see Files
	 * @see Files#list(Path)
	 * @see Files#isRegularFile(Path, java.nio.file.LinkOption...)
	 *      Files.isRegularFile(Path, LinkOption...)
	 * @see Files#newBufferedReader(Path, java.nio.charset.Charset)
	 *      Files.newBufferedReader(Path, Charset)
	 * @see Path
	 * @see Stream
	 * @see Stream#collect(java.util.stream.Collector) Stream.collect(Collector)
	 * @see Collectors
	 * @see Collectors#toCollection(java.util.function.Supplier)
	 *      Collectors.toCollection(Supplier)
	 */
	private static void executarRequisicao() {
		ExecutaRequisicaoSOAP.LOGGER.info("Entrando na rotina de execu\u00E7\u00E3o da requisi\u00E7\u00E3o SOAP em: " + DateFormatUtils.format(System.currentTimeMillis(), "dd/MM/yyyy HH:mm:ss.SSS"));

		try (final Stream<Path> arquivos = Files.list(ExecutaRequisicaoSOAP.DIRETORIO)
				.filter(path -> Files.isRegularFile(path) && ExecutaRequisicaoSOAP.recuperarExtensaoArquivo(path).endsWith(ExecutaRequisicaoSOAP.EXTENSAO_PENDING.toLowerCase(Locale.getDefault())))) {
			final Collection<Path> arquivosAsCollection = arquivos.collect(Collectors.toCollection(ArrayList::new));
			ExecutaRequisicaoSOAP.LOGGER
					.info("VERIFICANDO SE EXISTEM ARQUIVOS ELEG\u00CDVEIS PARA A ROTINA DE EXECU\u00C7\u00C3O DA REQUISI\u00C7\u00C3O SOAP.\nQuantidade de arquivo(s) para processar: "
							+ arquivosAsCollection.size());

			for (final Path caminho : arquivosAsCollection) {
				final StringBuilder corpoRequisicao = new StringBuilder();
				String configuracoes = null;
				String url = null;
				MimeHeaders mimeHeaders = null;

				// Como dito no javadoc da classe, a extensão do arquivo é utilizada como
				// status, então para evitarmos repetições com robôs de outros usuários, mudamos
				// a extensão para doing.
				final Path doing = ExecutaRequisicaoSOAP.renomearArquivo(caminho, ExecutaRequisicaoSOAP.EXTENSAO_DOING);

				try (BufferedReader reader = Files.newBufferedReader(doing, StandardCharsets.UTF_8)) {
					// Leitura do arquivo. A primeira linha contém as "configurações" do mesmo.
					configuracoes = reader.readLine();

					// Senão tivermos configurações o arquivo é inválido. Devemos avisar e seguir
					// para o próximo.
					if (StringUtils.isBlank(configuracoes)) {
						ExecutaRequisicaoSOAP.LOGGER.error("Arquivo inv\u00E1lido, pois n\u00E3o cont\u00E9m as configura\u00E7\u00F5es da requisi\u00E7\u00E3o SOAP.");
						continue;
					}

					// Caso tenhamos ";" na linha, sabemos que temos um job com necessidade de
					// utilização de autenticação com Basic Authentication.
					final String[] headers = configuracoes.split(";");
					if (headers.length > 1) {
						mimeHeaders = new MimeHeaders();

						// Recupera a URL.
						url = headers[0];

						// Recupera e encoda em Base64 o login e a senha.
						mimeHeaders.addHeader("Authorization", "Basic " + Base64.encodeBase64String(headers[1].getBytes(StandardCharsets.UTF_8)));
					} else {
						// Caso contrário temos só a URL mesmo.
						url = configuracoes;
					}

					// Recuperando o envelope SOAP propriamente dito.
					String linha = null;
					while ((linha = reader.readLine()) != null) {
						corpoRequisicao.append(linha);
					}
				}

				// Senão tivermos corpo da requisição o arquivo é inválido. Devemos avisar e
				// seguir para o próximo.
				if (corpoRequisicao.length() == 0) {
					ExecutaRequisicaoSOAP.LOGGER.error("Arquivo inv\u00E1lido, pois n\u00E3o cont\u00E9m o corpo (envelope SOAP) da requisi\u00E7\u00E3o SOAP.");
					continue;
				}

				// Cria o objeto com a mensagem SOAP a ser enviada.
				final SOAPMessage message = MessageFactory.newInstance().createMessage(mimeHeaders, new ByteArrayInputStream(corpoRequisicao.toString().getBytes(StandardCharsets.UTF_8)));

				// Recupera a resposta depois de executada a requisição com a mensagem SOAP
				// acima.
				final SOAPMessage response = SOAPConnectionFactory.newInstance().createConnection().call(message, url.trim());

				// Renomeia arquivo de entrada para constar como feito através da extensão DONE.
				ExecutaRequisicaoSOAP.renomearArquivo(doing, ExecutaRequisicaoSOAP.EXTENSAO_DONE);

				// Escreve arquivo de resposta do tipo RESPONSE num stream e utiliza um writer
				// para criar o arquivo propriamente dito.
				try (OutputStream out = Files.newOutputStream(Paths.get(ExecutaRequisicaoSOAP.recuperarCaminhoArquivoSemExtensao(caminho) + ExecutaRequisicaoSOAP.EXTENSAO_RESPONSE),
						StandardOpenOption.WRITE); Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
					response.writeTo(out);

					writer.flush();
				}
			}
		} catch (final IOException | SOAPException | RuntimeException e) {
			ExecutaRequisicaoSOAP.LOGGER.error("Erro inesperado ao executar requisi\u00E7\u00E3o SOAP. ERRO: " + e.getMessage(), e);
		}
	}

	/**
	 * M&eacute;todo respons&aacute;vel por renomear um arquivo utilizado para uma
	 * das extens&otilde;es reconhecidas por este rob&ocirc;.
	 *
	 * @param origem
	 *            Objeto do tipo {@link Path} representando um arquivo f&iacute;sico
	 *            do sistema contendo o caminho absoluto do arquivo a ser renomeado.
	 * @param extensaoNova
	 *            Objeto do tipo {@link String} contendo uma das extens&otilde;es
	 *            aceitas pelo rob&ocirc;. Esta extens&atilde;o &eacute; verificada
	 *            antes de renomear o arquivo de fato.
	 *
	 * @return Objeto do tipo {@link Path} contendo a representa&ccedil;&atilde;o do
	 *         caminho absoluto do arquivo a ser renomeado.
	 *
	 * @throws IOException
	 *             Esta exce&ccedil;&atilde;o &eacute; lan&ccedil;ada por um dos
	 *             dois motivos:
	 *             <ol>
	 *             <li>Pelo m&eacute;todo
	 *             {@link Files#move(Path, Path, java.nio.file.CopyOption...)
	 *             Files.move(Path, Path, CopyOption...)} e relan&ccedil;ada por
	 *             este m&eacute;todo para que seja tratado por quem o chamou;</li>
	 *             <li>Caso seja passado no par&acirc;metro
	 *             <code><strong>extensaoNova</strong></code> um valor que
	 *             n&atilde;o esteja definido por uma das seguintes constantes
	 *             abaixo:
	 *             <ul>
	 *             <li>{@link ExecutaRequisicaoSOAP#EXTENSAO_DOING
	 *             EXTENSAO_DOING}</li>
	 *             <li>{@link ExecutaRequisicaoSOAP#EXTENSAO_DONE
	 *             EXTENSAO_DONE}</li>
	 *             <li>{@link ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE
	 *             EXTENSAO_RESPONSE}</li>
	 *             </ul>
	 *             </li>
	 *             </ol>
	 * @throws SecurityException
	 *             Lan&ccedil;ada pelo m&eacute;todo
	 *             {@link Files#move(Path, Path, java.nio.file.CopyOption...)
	 *             Files.move(Path, Path, CopyOption...)} e relan&ccedil;ada por
	 *             este m&eacute;todo para que seja tratado por quem o chamou.
	 * @throws UnsupportedOperationException
	 *             Lan&ccedil;ada pelo m&eacute;todo
	 *             {@link Files#move(Path, Path, java.nio.file.CopyOption...)
	 *             Files.move(Path, Path, CopyOption...)} e relan&ccedil;ada por
	 *             este m&eacute;todo para que seja tratado por quem o chamou.
	 *
	 * @see ExecutaRequisicaoSOAP#isExtensaoValida(String) isExtensaoValida(String)
	 * @see ExecutaRequisicaoSOAP#recuperarCaminhoArquivoSemExtensao(Path)
	 *      recuperarCaminhoArquivoSemExtensao(Path)
	 * @see String
	 * @see Path
	 * @see java.nio.file.CopyOption CopyOption
	 * @see StandardCopyOption
	 * @see StandardCopyOption#REPLACE_EXISTING
	 * @see Files#move(Path, Path, java.nio.file.CopyOption...) Files.move(Path,
	 *      Path, CopyOption...)
	 */
	private static Path renomearArquivo(final Path origem, final String extensaoNova) throws IOException {
		if (!ExecutaRequisicaoSOAP.isExtensaoValida(extensaoNova)) {
			throw new IOException("Extens\u00E3o inv\u00E1lida passada por par\u00E2metro para o m\u00E9todo ExecutaRequisicaoSOAP#renomearArquivo(Path, String).");
		}

		// Renomeando (mesmo com o nome do método pela API Java sendo estranho) e
		// retornando caminho absoluto do arquivo renomeado.
		return Files.move(origem, origem.resolveSibling(ExecutaRequisicaoSOAP.recuperarCaminhoArquivoSemExtensao(origem) + extensaoNova), StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * M&eacute;todo respons&aacute;vel por excluir os arquivos dos tipos definidos
	 * pelas constantes {@link ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING},
	 * {@link ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE} e
	 * {@link ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE EXTENSAO_RESPONSE} no
	 * diret&oacute;rio definido pela constante
	 * {@link ExecutaRequisicaoSOAP#DIRETORIO DIRETORIO} que tenham sido criado
	 * h&aacute; mais de uma hora.
	 *
	 * @see ExecutaRequisicaoSOAP#DIRETORIO DIRETORIO
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE EXTENSAO_RESPONSE
	 * @see ExecutaRequisicaoSOAP#recuperarExtensaoArquivo(Path)
	 *      recuperarExtensaoArquivo(Path)
	 * @see Files
	 * @see Files#newDirectoryStream(Path, java.nio.file.DirectoryStream.Filter)
	 * @see Files#list(Path)
	 * @see Files#getLastModifiedTime(Path, java.nio.file.LinkOption...)
	 * @see Files#isRegularFile(Path, java.nio.file.LinkOption...)
	 *      Files.isRegularFile(Path, LinkOption...)
	 * @see File#lastModified()
	 * @see Files#delete(Path)
	 * @see Iterable#forEach(java.util.function.Consumer) Iterable.forEach(Consumer)
	 * @see System#currentTimeMillis()
	 */
	private static void excluirArquivos() {
		// Recupero os arquivos do tipo definido pela constante criado há mais de uma
		// hora, transformo numa lista e excluo.
		try (final DirectoryStream<Path> arquivos = Files.newDirectoryStream(ExecutaRequisicaoSOAP.DIRETORIO,
				path -> Files.isRegularFile(path) && System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis() >= 360000 && ExecutaRequisicaoSOAP.isExtensaoValida(path))) {
			arquivos.forEach(path -> {
				try {
					Files.delete(path);
				} catch (final IOException e) {
					ExecutaRequisicaoSOAP.LOGGER.error("Erro inesperado ao excluir arquivo " + path.getFileName().toString() + ". ERRO: " + e.getMessage(), e);
				}
			});
		} catch (final IOException e) {
			ExecutaRequisicaoSOAP.LOGGER.error("Erro inesperado ao listar arquivos do diret\u00F3rio " + ExecutaRequisicaoSOAP.DIRETORIO.toString() + ". ERRO: ", e);
		}
	}

	/**
	 * M&eacute;todo respons&aacute;vel por dado um arquivo f&iacute;sico do sistema
	 * contendo o caminho absoluto do mesmo, retornar o pr&oacute;prio caminho sem a
	 * extens&atilde;o do mesmo.
	 *
	 * @param caminho
	 *            Objeto do tipo {@link Path} contendo a representa&ccedil;&atilde;o
	 *            do caminho absoluto de um arquivo f&iacute;sico ou de seu nome.
	 *
	 * @return Objeto do tipo {@link String} contendo o caminho absoluto do
	 *         {@link File arquivo} sem a sua extens&atilde;o.
	 *
	 * @see Path
	 * @see ExecutaRequisicaoSOAP#substring(Path, boolean) substring(Path, boolean)
	 */
	private static String recuperarCaminhoArquivoSemExtensao(final Path caminho) {
		return ExecutaRequisicaoSOAP.substring(caminho, false);
	}

	/**
	 * M&eacute;todo respons&aacute;vel por dado o caminho absoluto de um arquivo,
	 * retornar sua extens&atilde;o com o s&iacute;mbolo de
	 * &quot;<code><strong>.</strong></code>&quot;.
	 *
	 * @param caminho
	 *            Objeto do tipo {@link Path} contendo a representa&ccedil;&atilde;o
	 *            do caminho absoluto de um arquivo f&iacute;sico ou de seu nome.
	 *
	 * @return Objeto do tipo {@link String} contendo apenas a extens&atilde;o deste
	 *         mesmo arquivo com o s&iacute;mbolo de
	 *         &quot;<code><strong>.</strong></code>&quot;.
	 *
	 * @see Path
	 * @see ExecutaRequisicaoSOAP#substring(Path) substring(Path)
	 */
	private static String recuperarExtensaoArquivo(final Path caminho) {
		return ExecutaRequisicaoSOAP.substring(caminho);
	}

	/**
	 * Sobrecarga para o m&eacute;todo
	 * {@link ExecutaRequisicaoSOAP#substring(Path, boolean) substring(Path,
	 * boolean)} passando o par&acirc;metro <code><strong>reverso</strong></code> do
	 * m&eacute;todo supracitado com o valor <code><strong>true</strong></code> por
	 * padr&atilde;o.
	 *
	 * @param caminho
	 *            Objeto do tipo {@link Path} contendo a representa&ccedil;&atilde;o
	 *            do caminho absoluto de um arquivo f&iacute;sico ou de seu nome.
	 *
	 * @return Objeto do tipo {@link String} contendo a
	 *         {@link String#substring(int)} quando o par&acirc;metro
	 *         <code><strong>reverso</strong></code> est&aacute; com o valor
	 *         <code><strong>true</strong></code> ou a
	 *         {@link String#substring(int, int)} quando o par&acirc;metro
	 *         <code><strong>reverso</strong></code> est&aacute; com o valor
	 *         <code><strong>false</strong></code> da {@link String} retornada pelo
	 *         m&eacute;todo {@link Path#toString()} em cima de
	 *         {@link Path#getFileName()}.
	 *
	 * @see ExecutaRequisicaoSOAP#substring(Path, boolean) substring(Path, boolean)
	 */
	private static String substring(final Path caminho) {
		return ExecutaRequisicaoSOAP.substring(caminho, true);
	}

	/**
	 * Sobrecarga para o m&eacute;todo
	 * {@link ExecutaRequisicaoSOAP#substring(Path, boolean, char) substring(Path,
	 * boolean, char)} passando o par&acirc;metro
	 * <code><strong>caracterSeparacao</strong></code> do m&eacute;todo supracitado
	 * com o valor <code><strong>&quot;.&quot;</strong></code> por padr&atilde;o.
	 *
	 * @param caminho
	 *            Objeto do tipo {@link Path} contendo a representa&ccedil;&atilde;o
	 *            do caminho absoluto de um arquivo f&iacute;sico ou de seu nome.
	 * @param reverso
	 *            O intr&iacute;nseco <code><strong>boolean</strong></code> que
	 *            determinar&aacute; se buscaremos at&eacute; acharmos a
	 *            &uacute;ltima representa&ccedil;&atilde;o do caracter passado por
	 *            par&acirc;metro <code><strong>caracterSeparacao</strong></code> ou
	 *            a partir do mesmo conforme documenta&ccedil;&atilde;o do
	 *            pr&oacute;prio m&eacute;todo.
	 *
	 * @return Objeto do tipo {@link String} contendo a
	 *         {@link String#substring(int)} quando o par&acirc;metro
	 *         <code><strong>reverso</strong></code> est&aacute; com o valor
	 *         <code><strong>true</strong></code> ou a
	 *         {@link String#substring(int, int)} quando o par&acirc;metro
	 *         <code><strong>reverso</strong></code> est&aacute; com o valor
	 *         <code><strong>false</strong></code> da {@link String} retornada pelo
	 *         m&eacute;todo {@link Path#toString()} em cima de
	 *         {@link Path#getFileName()}.
	 *
	 * @see ExecutaRequisicaoSOAP#substring(Path, boolean, char) substring(Path,
	 *      boolean, char)
	 */
	private static String substring(final Path caminho, final boolean reverso) {
		return ExecutaRequisicaoSOAP.substring(caminho, reverso, '.');
	}

	/**
	 * M&eacute;todo respons&aacute;vel por fazer o que faz o m&eacute;todo
	 * {@link String#substring(int)} quando o par&acirc;metro
	 * <code><strong>reverso</strong></code> est&aacute; com o valor
	 * <code><strong>true</strong></code> e o que faz o m&eacute;todo
	 * {@link String#substring(int, int)} quando o par&acirc;metro
	 * <code><strong>reverso</strong></code> est&aacute; com o valor
	 * <code><strong>false</strong></code> sem o overhead das
	 * valida&ccedil;&otilde;es feitas em ambos os m&eacute;todos supracitados
	 * levando em considera&ccedil;&atilde;o a &uacute;ltima posi&ccedil;&atilde;o
	 * do par&acirc;metro <code><strong>caracterSeparacao</strong></code> obtido da
	 * {@link String} retornada pelo m&eacute;todo {@link Path#toString()} em cima
	 * de {@link Path#getFileName()}.
	 *
	 * @param caminho
	 *            Objeto do tipo {@link Path} contendo a representa&ccedil;&atilde;o
	 *            do caminho absoluto de um arquivo f&iacute;sico ou de seu nome.
	 * @param reverso
	 *            O intr&iacute;nseco <code><strong>boolean</strong></code> que
	 *            determinar&aacute; se buscaremos at&eacute; acharmos a
	 *            &uacute;ltima representa&ccedil;&atilde;o do caracter passado por
	 *            par&acirc;metro <code><strong>caracterSeparacao</strong></code> ou
	 *            a partir do mesmo conforme documenta&ccedil;&atilde;o do
	 *            pr&oacute;prio m&eacute;todo.
	 * @param caracterSeparacao
	 *            O intr&iacute;nseco <code><strong>char</strong></code> contendo o
	 *            valor do caracter que dever&aacute; ter sua &uacute;ltima
	 *            ocorr&ecirc;ncia buscado na {@link String} obtida atrav&eacute;s
	 *            do retorno do m&eacute;todo {@link Path#toString()} em cima de
	 *            {@link Path#getFileName()} conforme documenta&ccedil;&atilde;o do
	 *            pr&oacute;prio m&eacute;todo.
	 *
	 * @return Objeto do tipo {@link String} contendo a
	 *         {@link String#substring(int)} quando o par&acirc;metro
	 *         <code><strong>reverso</strong></code> est&aacute; com o valor
	 *         <code><strong>true</strong></code> ou a
	 *         {@link String#substring(int, int)} quando o par&acirc;metro
	 *         <code><strong>reverso</strong></code> est&aacute; com o valor
	 *         <code><strong>false</strong></code> da {@link String} retornada pelo
	 *         m&eacute;todo {@link Path#toString()} em cima de
	 *         {@link Path#getFileName()}.
	 *
	 * @see String
	 * @see String#lastIndexOf(int)
	 * @see String#substring(int)
	 * @see String#substring(int, int)
	 * @see Path
	 * @see Path#getFileName()
	 * @see Path#toString()
	 */
	private static String substring(final Path caminho, final boolean reverso, final char caracterSeparacao) {
		final String nomeArquivo = caminho.getFileName().toString();
		final int posicaoUltimoPonto = nomeArquivo.lastIndexOf(Character.isValidCodePoint(caracterSeparacao) ? caracterSeparacao : '.');
		final char[] nomeArquivoAsCharArray = nomeArquivo.toCharArray();
		return reverso ? new String(nomeArquivoAsCharArray, posicaoUltimoPonto, nomeArquivoAsCharArray.length - posicaoUltimoPonto) : new String(nomeArquivoAsCharArray, 0, posicaoUltimoPonto);
	}

	/**
	 * Sobrecarga para o m&eacute;todo
	 * {@link ExecutaRequisicaoSOAP#isExtensaoValida(String)
	 * isExtensaoValida(String)} passando o par&acirc;metro
	 * <code><strong>caminho</strong></code> do tipo {@link Path} contendo a
	 * representa&ccedil;&atilde;o do caminho absoluto de um arquivo f&iacute;sico
	 * ou de seu nome, que ser&aacute; previamente utilizado pelo m&eacute;todo
	 * {@link ExecutaRequisicaoSOAP#recuperarExtensaoArquivo(Path)
	 * recuperarExtensaoArquivo(Path)} de maneira a retornar apenas a
	 * extens&atilde;o contida no par&acirc;metro deste m&eacute;todo em si. Tal
	 * retorno sim, ser&aacute; passado para o m&eacute;todo
	 * {@link ExecutaRequisicaoSOAP#isExtensaoValida(String)
	 * isExtensaoValida(String)}.
	 *
	 * @param caminho
	 *            Objeto do tipo {@link Path} contendo a representa&ccedil;&atilde;o
	 *            do caminho absoluto de um arquivo f&iacute;sico ou de seu nome.
	 *
	 * @return O intr&iacute;nseco <code><strong>boolean</strong></code> contendo o
	 *         valor <code><strong>true</strong></code> caso o valor do
	 *         par&acirc;metro <code><strong>extensao</strong></code> seja igual
	 *         &agrave constante {@link ExecutaRequisicaoSOAP#EXTENSAO_DOING
	 *         EXTENSAO_DOING}, {@link ExecutaRequisicaoSOAP#EXTENSAO_DONE
	 *         EXTENSAO_DONE} ou {@link ExecutaRequisicaoSOAP#EXTENSAO_DONE
	 *         EXTENSAO_DONE} ou com o valor <code><strong>false</strong></code>
	 *         caso contr&aacute;rio.
	 *
	 * @see Path
	 * @String
	 * @see ExecutaRequisicaoSOAP#isExtensaoValida(String) isExtensaoValida(String)
	 * @see ExecutaRequisicaoSOAP#recuperarExtensaoArquivo(Path)
	 *      recuperarExtensaoArquivo(Path)
	 */
	private static boolean isExtensaoValida(final Path caminho) {
		return ExecutaRequisicaoSOAP.isExtensaoValida(ExecutaRequisicaoSOAP.recuperarExtensaoArquivo(caminho));
	}

	/**
	 * M&eacute;todo respons&aacute;vel por retornar o intr&iacute;nseco
	 * <code><strong>boolean</strong></code> contendo o valor
	 * <code><strong>true</strong></code> caso o valor do par&acirc;metro
	 * <code><strong>extensao</strong></code> seja igual &agrave constante
	 * {@link ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING},
	 * {@link ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE} ou
	 * {@link ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE} ou com o valor
	 * <code><strong>false</strong></code> caso contr&aacute;rio.
	 *
	 * @param extensao
	 *            Objeto do tipo {@link String} contendo o valor da extens&atilde;o
	 *            de arquivo a ser testada.
	 *
	 * @return O intr&iacute;nseco <code><strong>boolean</strong></code> contendo o
	 *         valor <code><strong>true</strong></code> caso o valor do
	 *         par&acirc;metro <code><strong>extensao</strong></code> seja igual
	 *         &agrave constante {@link ExecutaRequisicaoSOAP#EXTENSAO_DOING
	 *         EXTENSAO_DOING}, {@link ExecutaRequisicaoSOAP#EXTENSAO_DONE
	 *         EXTENSAO_DONE} ou {@link ExecutaRequisicaoSOAP#EXTENSAO_DONE
	 *         EXTENSAO_DONE} ou com o valor <code><strong>false</strong></code>
	 *         caso contr&aacute;rio.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING EXTENSAO_DOING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE EXTENSAO_DONE
	 */
	private static boolean isExtensaoValida(final String extensao) {
		return extensao.endsWith(ExecutaRequisicaoSOAP.EXTENSAO_DOING) || extensao.endsWith(ExecutaRequisicaoSOAP.EXTENSAO_DONE) || extensao.endsWith(ExecutaRequisicaoSOAP.EXTENSAO_RESPONSE);
	}
}