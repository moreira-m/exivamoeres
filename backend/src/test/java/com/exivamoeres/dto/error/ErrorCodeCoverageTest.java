package com.exivamoeres.dto.error;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>Toda recusa de regra alcançável tem código</b> (item T18).
 *
 * <p>O T2 converteu 10 recusas e deixou ~18 em português, com a frase servindo de reserva.
 * O T18 fechou a conta. O problema de "fechar a conta" é que ela reabre sozinha: a próxima
 * pessoa escreve {@code throw new BusinessRuleException("frase")}, o código compila, o teste
 * passa, o site responde — e quem usa em inglês lê português. <b>Ninguém descobre</b> até
 * alguém tomar aquela recusa específica no idioma errado.</p>
 *
 * <p>Este teste lê o <b>código-fonte</b> e reprova quem acrescentar recusa sem código. É a
 * contraparte do {@code frontend/scripts/error-codes-check.mjs}, que fecha o outro lado (o
 * código existe no Java e falta tradução).</p>
 *
 * <p>⚠️ <b>Fonte, e não reflexão</b>, de propósito: o que se quer proibir é uma <b>chamada de
 * construtor</b>, e isso não existe em runtime — só o objeto lançado, se e quando alguém
 * chegar naquele ramo. Metade das recusas está em caminho difícil de alcançar (é o motivo de
 * elas terem passado batidas até aqui), então esperar que um teste as execute é esperar
 * justamente o que não acontece.</p>
 */
class ErrorCodeCoverageTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /**
     * Cada chamada de {@code new BusinessRuleException(} — uma por ocorrência, não uma por
     * linha.
     *
     * ⚠️ A primeira versão varria com uma janela deslizante de N linhas e dava <b>falso
     * positivo</b>: a mesma chamada casava em vários deslocamentos, e nos mais tardios o
     * `ErrorCode` já tinha ficado fora da janela. Casar por <b>posição no texto todo</b>
     * resolve — o primeiro argumento vem antes da frase, sempre.
     */
    private static final Pattern RECUSA = Pattern.compile("new BusinessRuleException\\(");

    /**
     * As exceções conscientes, com o motivo de cada uma. Lista fechada: entrada nova aqui
     * é uma decisão a ser defendida no code review, não um lugar para esconder pendência.
     */
    private static final Map<String, String> PERMITIDAS = Map.of(
            "SuggestionServiceImpl.java",
            "sugestões de soul core são código DORMENTE (o controller foi removido — ver docs/1 §2): "
                    + "converter agora seria inventar tradução para tela que não existe, e o destino "
                    + "delas é o item T4",
            "SoulcoreServiceImpl.java",
            "idem — soul cores dormentes",
            "HuntingListServiceImpl.java",
            "uma ocorrência: a reserva de `comMotivoAninhado`, que mantém o reembrulho antigo "
                    + "quando o motivo interno NÃO tem código. É a rede da migração incremental do T2");

    /** Quantas recusas sem código cada arquivo da lista pode ter. */
    private static final Map<String, Integer> TETO = Map.of(
            "SuggestionServiceImpl.java", 1,
            "SoulcoreServiceImpl.java", 1,
            "HuntingListServiceImpl.java", 1);

    @Test
    void nenhumaRecusaAlcancavelFicaSemCodigo() throws IOException {
        List<String> semCodigo = new ArrayList<>();
        Map<String, Integer> contagemPermitida = new java.util.HashMap<>();

        for (Path arquivo : fontes()) {
            String nome = arquivo.getFileName().toString();
            String fonte = Files.readString(arquivo);
            Matcher m = RECUSA.matcher(fonte);
            while (m.find()) {
                if (temCodigo(fonte, m.end())) {
                    continue;
                }
                if (PERMITIDAS.containsKey(nome)) {
                    contagemPermitida.merge(nome, 1, Integer::sum);
                } else {
                    semCodigo.add(nome + ":" + linhaDe(fonte, m.start()));
                }
            }
        }

        assertThat(semCodigo)
                .as("""
                        Recusa de regra sem ErrorCode: ela responde 422 com a frase em \
                        português e `code: null`, então quem usa o site em inglês lê \
                        português. Acrescente o valor em ErrorCode.java, o `.with(...)` \
                        dos valores da frase, e a chave `errors.codes.<CODE>` nos dois \
                        idiomas (o script do frontend confere). Se a recusa NÃO é regra de \
                        negócio — rate limit, falha interna — o certo é o status, não o \
                        código: 429 ou 5xx.""")
                .isEmpty();

        // E o teto das permitidas: se uma delas cresce, alguém acrescentou recusa sem código
        // num arquivo que já tinha licença — que é como uma exceção consciente vira porta.
        assertThat(contagemPermitida)
                .as("as exceções conscientes não podem crescer; o motivo de cada uma está em PERMITIDAS")
                .allSatisfy((nome, quantas) -> assertThat(quantas).isLessThanOrEqualTo(TETO.get(nome)));
    }

    @Test
    void todoCodigoDoEnumEhUsadoEmAlgumaRecusa() throws IOException {
        // O outro lado da mesma moeda: código declarado e nunca lançado é frase morta em dois
        // idiomas — e faz o próximo leitor achar que aquela recusa existe.
        String todasAsFontes = fontes().stream()
                .map(f -> {
                    try {
                        return Files.readString(f);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                })
                .reduce("", String::concat);

        List<String> naoUsados = Stream.of(ErrorCode.values())
                .map(Enum::name)
                .filter(nome -> !todasAsFontes.contains("ErrorCode." + nome))
                .toList();

        assertThat(naoUsados)
                .as("código declarado e nunca lançado vira frase órfã nos dois idiomas")
                .isEmpty();
    }

    /**
     * O primeiro argumento é um {@code ErrorCode}?
     *
     * <p>Olha do abre-parêntese até a <b>frase</b> (o primeiro {@code "}), que é o que vem
     * depois do código por convenção. Sem frase literal — {@code new
     * BusinessRuleException(frase)} — vale até o fecha-parêntese.</p>
     */
    private static boolean temCodigo(String fonte, int depoisDoParentese) {
        int aspas = fonte.indexOf('"', depoisDoParentese);
        int fecha = fonte.indexOf(')', depoisDoParentese);
        int fim = aspas < 0 ? fecha : (fecha < 0 ? aspas : Math.min(aspas, fecha));
        return fim > depoisDoParentese
                && fonte.substring(depoisDoParentese, fim).contains("ErrorCode.");
    }

    private static int linhaDe(String fonte, int posicao) {
        return (int) fonte.substring(0, posicao).chars().filter(c -> c == '\n').count() + 1;
    }

    private static List<Path> fontes() throws IOException {
        try (Stream<Path> caminhada = Files.walk(MAIN)) {
            return caminhada.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
