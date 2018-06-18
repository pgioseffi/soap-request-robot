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

/**
 * <p>
 * Classe respons&aacute;vel por executar requisi&ccedil;&otilde;es SOAP a cada
 * cinco segundos atrav&eacute;s da leitura de um arquivo <code>PENDING</code>
 * com conte&uacute;do XML com um envelope SOAP v&aacute;lido e manter por uma
 * hora a resposta desta mesma requisi&ccedil;&atilde;o em um arquivo
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
 * <li>PENDING: Arquivos com requisi&ccedil;&otilde;es pendentes;</li>
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
 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING
 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE
 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE
 */
public class ExecutaRequisicaoSOAP {

	/**
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>RESPONSE</code>.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE
	 * @see ExecutaRequisicaoSOAP#EXTENSOES
	 */
	private static final String EXTENSAO_RESPONSE = ".RESPONSE";

	/**
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>DONE</code>.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE
	 * @see ExecutaRequisicaoSOAP#EXTENSOES
	 */
	private static final String EXTENSAO_DONE = ".DONE";

	/**
	 * Constante utilizada para a manipula&ccedil;&atilde;o de arquivos do tipo
	 * <code>DOING</code>.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE
	 * @see ExecutaRequisicaoSOAP#EXTENSOES
	 */
	private static final String EXTENSAO_DOING = ".DOING";

	/**
	 * Constante utilizada para a manipula&ccedil;&atilde;o das extens&otilde;es que
	 * servem como &quot;status&quot deste rob&ocirc;.
	 *
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DOING
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_DONE
	 * @see ExecutaRequisicaoSOAP#EXTENSAO_RESPONSE
	 */
	// TODO: Usar Collection ao invés de List.
	private static final List<String> EXTENSOES = Arrays.asList(ExecutaRequisicaoSOAP.EXTENSAO_RESPONSE,
			ExecutaRequisicaoSOAP.EXTENSAO_DONE, ExecutaRequisicaoSOAP.EXTENSAO_DOING);

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
	 * @see ExecutaRequisicaoSOAP#DIRETORIO
	 * @see ExecutaRequisicaoSOAP#CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO
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
	 * arquivos do tipo <code>PENDING</code> com as requisi&ccedil;&otilde;es a
	 * serem feitas em formato de envelope SOAP.
	 *
	 * @see Properties
	 * @see ExecutaRequisicaoSOAP#ARQUIVO_PROPERTIES
	 */
	private static final String DIRETORIO = ExecutaRequisicaoSOAP.ARQUIVO_PROPERTIES.getProperty("diretorio");

	/**
	 * Constante utilizada para manter o nome do arquivo que controla se o job
	 * j&aacute; est&aacute; em execu&ccedil;&atilde;o ou n&atilde;o por um
	 * determinado usu&aacute;rio.
	 *
	 * @see Properties
	 * @see ExecutaRequisicaoSOAP#ARQUIVO_PROPERTIES
	 */
	private static final String CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO = ExecutaRequisicaoSOAP.DIRETORIO
			+ File.separatorChar
			+ ExecutaRequisicaoSOAP.ARQUIVO_PROPERTIES.getProperty("nome.arquivo.controle.execucao");

	// TODO: Construir construtor padrão privado para inibir instância desta classe.

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
	 * @see ExecutaRequisicaoSOAP#executarRequisicao()
	 * @see ExecutaRequisicaoSOAP#excluirArquivos()
	 * @see ExecutaRequisicaoSOAP#singletonJob()
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
		// Iniciando arquivo de controle.
		final File singleton = new File(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO);

		if (singleton.exists()) {
			// Se o mesmo já existir, o robô já está sendo executado por um usuário. Log e
			// saia.
			// TODO: Extrair para variável mensagem abaixo, visto que a mesma é utilizada em
			// mais de um local.
			ExecutaRequisicaoSOAP.LOGGER.error("Job em execu\u00E7\u00E3o.");

			// Sai da execução sinalizando condição aceitável.
			Runtime.getRuntime().exit(0);
		} else {
			// Senão estiver sendo executado, crie um arquivo com uma mensagem padrão.
			try (Writer writer = new BufferedWriter(
					new FileWriter(ExecutaRequisicaoSOAP.CAMINHO_ABSOLUTO_ARQUIVO_CONTROLE_EXECUCAO))) {
				writer.write("Job em execu\u00E7\u00E3o.");
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
	 * M&eacute;todo respons&aacute;vel por varrer o diret&oacute;rio com os
	 * arquivos do tipo <code>PENDING</code> e executar a requisi&ccedil;&atilde;o.
	 *
	 * @see ExecutaRequisicaoSOAP#constroiArquivosESubmeteRequisicao(File)
	 */
	private static void executarRequisicao() {
		ExecutaRequisicaoSOAP.LOGGER.info("Entrando na rotina de execu\u00E7\u00E3o da requisi\u00E7\u00E3o SOAP em: "
				+ DateFormatUtils.format(System.currentTimeMillis(), "dd/MM/yyyy HH:mm:ss.SSS"));

		// Instanciando objeto file com caminho do diretório que será utilizado para
		// varremos em busca de arquivos ".PENDING" com as requisições SOAP.
		final File dir = new File(ExecutaRequisicaoSOAP.DIRETORIO);
		if (dir.exists()) {
			ExecutaRequisicaoSOAP.LOGGER.info("VERIFICANDO  DIRET\u00D3RIO: " + dir.getAbsolutePath());

			// Buscando no diretório apenas arquivos do tipo ".PENDING".
			final File[] arquivos = dir.listFiles((diretorio, name) -> ExecutaRequisicaoSOAP
					.recuperarExtensaoArquivo(name).equalsIgnoreCase(".PENDING"));

			ExecutaRequisicaoSOAP.LOGGER.info(
					"VERIFICANDO SE EXISTEM ARQUIVOS ELEG\u00CDVEIS PARA A ROTINA DE EXECU\u00C7\u00C3O DA REQUISI\u00C7\u00C3O SOAP.\nQuantidade de arquivo(s) para processar: "
							+ arquivos.length);

			// Varre a lista de arquivos encontrados e submete a requisição.
			Arrays.asList(arquivos).forEach(f -> ExecutaRequisicaoSOAP.constroiArquivosESubmeteRequisicao(f));
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
	private static void constroiArquivosESubmeteRequisicao(final File arquivo) {
		try {
			final StringBuilder corpoRequisicao = new StringBuilder();
			String configuracoes = null;
			String url = null;
			MimeHeaders mimeHeaders = null;

			// Como dito no javadoc da classe, a extensão do arquivo é utilizada como
			// status, então para evitarmos repetições com robôs de outros usuários, mudamos
			// a extensão para doing.
			final String doing = ExecutaRequisicaoSOAP.renomearArquivo(arquivo.getAbsolutePath(),
					ExecutaRequisicaoSOAP.EXTENSAO_DOING);

			try (BufferedReader reader = new BufferedReader(new FileReader(doing))) {
				// Leitura do arquivo. A primeira linha contém as "configurações" do mesmo.
				configuracoes = reader.readLine();

				// Senão tivermos configurações o arquivo é inválido. Devemos avisar e seguir
				// para o próximo.
				// TODO: Logar.
				if (StringUtils.isBlank(configuracoes)) {
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
			// TODO: Logar.
			if (corpoRequisicao.length() == 0) {
				return;
			}

			// Cria o objeto com a mensagem SOAP a ser enviada.
			final SOAPMessage message = MessageFactory.newInstance().createMessage(mimeHeaders,
					new ByteArrayInputStream(corpoRequisicao.toString().getBytes(StandardCharsets.UTF_8)));

			// Recupera a resposta depois de executada a requisição com a mensagem SOAP
			// acima.
			final SOAPMessage response = SOAPConnectionFactory.newInstance().createConnection().call(message,
					url.trim());

			// Escreve a resposta num stream.
			// TODO: Verificar possibilidade de utilizar FileOutputStream e eliminar
			// overhead no filewriter abaixo.
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
	 * M&eacute;todo respons&aacute;vel por renomear um arquivo utilizado para uma
	 * das extens&otilde;es reconhecidas por este rob&ocirc;.
	 *
	 * @param caminhoAbsolutoArquivo
	 *            Variável do tipo {@link String} contendo o caminho absoluto do
	 *            arquivo a ser renomeado.
	 * @param extensaoNova
	 *            Variável do tipo {@link String} contendo uma das extensões aceitas
	 *            pelo robô.
	 *
	 * @return Objeto do tipo {@link String} contendo o caminho absoluto do arquivo
	 *         renomeado.
	 *
	 * @throws IOException
	 *             Exce&ccedil;&atilde;o lan&ccedil;ada pelo m&eacute;todo
	 *             {@link Files#move(Path, Path, java.nio.file.CopyOption...)
	 *             Files#move(Path, Path, CopyOption...)} e relan&ccedil;ada por
	 *             este m&eacute;todo para que seja tratado por quem o chamou.
	 *
	 * @see String
	 * @see Path
	 * @see java.nio.file.CopyOption CopyOption
	 * @see StandardCopyOption
	 * @see Files#move(Path, Path, java.nio.file.CopyOption...)
	 * @see ExecutaRequisicaoSOAP#recuperarCaminhoArquivoSemExtensao(String)
	 */
	// TODO: Fazer verificação se parâmetro extensaoNova é um dos aceitáveis pelo
	// robô.
	private static String renomearArquivo(final String caminhoAbsolutoArquivo, final String extensaoNova)
			throws IOException {
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
	 * @see ExecutaRequisicaoSOAP#DIRETORIO
	 * @see ExecutaRequisicaoSOAP#EXTENSOES
	 * @see File#lastModified()
	 * @see File#delete()
	 * @see System#currentTimeMillis()
	 */
	private static void excluirArquivos() {
		final File diretorio = new File(ExecutaRequisicaoSOAP.DIRETORIO);

		if (diretorio.exists()) {
			// Se o diretório existir, recupero os arquivos do tipo definido pela constante
			// criado há mais de uma hora, transformo numa lista e excluo.
			Arrays.asList(diretorio.listFiles((file) -> ExecutaRequisicaoSOAP.EXTENSOES
					.contains(ExecutaRequisicaoSOAP.recuperarExtensaoArquivo(file.getName()))
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
	 * {@linkplain File#getAbsolutePath()} contendo o caminho absoluto do arquivo.
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
	 * @see ExecutaRequisicaoSOAP#recuperarExtensaoArquivo(String)
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
	 * @see String
	 * @see String#lastIndexOf(int)
	 * @see String#substring(int, int)
	 */
	// TODO: Validar se arquivo é arquivo e não diretório.
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
	// TODO: Validar se arquivo é arquivo e não diretório.
	private static String recuperarExtensaoArquivo(final String nomeArquivo) {
		return nomeArquivo.substring(nomeArquivo.lastIndexOf('.'));
	}
}