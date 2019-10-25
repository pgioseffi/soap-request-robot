package br.com.pgioseffi.requisicoes.soap.benchmarking;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: JavaDocs.
public class BenchmarkingExecutaRequisicaoSOAP {

	private static final String EXTENSAO_RESPONSE = ".RESPONSE";
	private static final String EXTENSAO_DONE = ".DONE";
	private static final String EXTENSAO_DOING = ".DOING";
	private static final String EXTENSAO_PENDING = ".PENDING";

	private static final Collection<String> EXTENSOES = Collections
			.unmodifiableList(Arrays.asList(BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_RESPONSE, BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_DONE, BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_DOING));

	public static void main(final String[] args) {
		final long t1 = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			System.out.println(BenchmarkingExecutaRequisicaoSOAP.EXTENSOES.contains(".RESPONSE"));
		}
		final long resultado01 = System.currentTimeMillis() - t1;

		final long t2 = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			System.out.println(".RESPONSE".endsWith(BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_RESPONSE) || ".RESPONSE".endsWith(BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_DONE)
					|| ".RESPONSE".endsWith(BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_DOING));
		}
		final long resultado02 = System.currentTimeMillis() - t2;

		final Path path = Paths.get("C:\\teste\\teste.pending");

		final long t3 = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			System.out.println(BenchmarkingExecutaRequisicaoSOAP.recuperarExtensaoArquivo(path).equalsIgnoreCase(BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_PENDING));
		}
		final long resultado03 = System.currentTimeMillis() - t3;

		final long t4 = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			System.out.println(BenchmarkingExecutaRequisicaoSOAP.recuperarExtensaoArquivo(path).endsWith(BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_PENDING.toLowerCase(Locale.getDefault())));
		}
		final long resultado04 = System.currentTimeMillis() - t4;

		final long t5 = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			System.out.println(path.endsWith(BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_PENDING));
		}
		final long resultado05 = System.currentTimeMillis() - t5;

		final long t6 = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			System.out.println(path.getFileName().endsWith(BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_PENDING.toLowerCase(Locale.getDefault())));
		}
		final long resultado06 = System.currentTimeMillis() - t6;

		final long t7 = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			System.out.println(path.toString().endsWith(BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_PENDING));
		}
		final long resultado07 = System.currentTimeMillis() - t7;

		final long t8 = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			System.out.println(path.getFileName().toString().endsWith(BenchmarkingExecutaRequisicaoSOAP.EXTENSAO_PENDING.toLowerCase(Locale.getDefault())));
		}
		final long resultado08 = System.currentTimeMillis() - t8;

		final long t9 = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			System.out.println(EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE));
		}
		final long resultado09 = System.currentTimeMillis() - t9;

		final long t10 = System.currentTimeMillis();
		for (int i = 0; i < 10000; i++) {
			System.out.println(Stream.of(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE).collect(Collectors.toSet()));
		}
		final long resultado10 = System.currentTimeMillis() - t10;

		for (int i = 0; i < 1000000; i++) {
			final Integer a = new Integer(10);
			final Integer b = new Integer(10);
			// Integer a = 10;
			// Integer b = 10;
			if (a == b) {
				System.out.println("Fim da execu\u00E7\u00E3o. I = " + i);
				return;
			}
		}

		System.out.println("Fim da execu\u00E7\u00E3o. Nunca houve igualdade.");
		System.out.println("Tempo 01: " + resultado01 + "\nTempo 02: " + resultado02 + "\nTempo 03: " + resultado03 + "\nTempo 04: " + resultado04 + "\nTempo 05: " + resultado05 + "\nTempo 06: "
				+ resultado06 + "\nTempo 07: " + resultado07 + "\nTempo 08: " + resultado08 + "\nTempo 09: " + resultado09 + "\nTempo 10: " + resultado10);
		System.out.println(path);
		System.out.println(path.getFileName());
		System.out.println(BenchmarkingExecutaRequisicaoSOAP.reverso(path.getFileName(), true));
		System.out.println(BenchmarkingExecutaRequisicaoSOAP.reverso(path.getFileName(), false));
	}

	private static String recuperarExtensaoArquivo(final Path caminho) {
		return BenchmarkingExecutaRequisicaoSOAP.reverso(caminho, true);
	}

	private static String reverso(final Path caminho, final boolean reverso) {
		final String caminhoAbsolutoArquivo = caminho.getFileName().toString();
		final int posicaoUltimoPonto = caminhoAbsolutoArquivo.lastIndexOf('.');
		final char[] caminhoAbsolutoArquivoAsCharArray = caminhoAbsolutoArquivo.toCharArray();
		return reverso ? new String(caminhoAbsolutoArquivoAsCharArray, posicaoUltimoPonto, caminhoAbsolutoArquivoAsCharArray.length - posicaoUltimoPonto)
				: new String(caminhoAbsolutoArquivoAsCharArray, 0, posicaoUltimoPonto);
	}
}