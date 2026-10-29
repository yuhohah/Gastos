# Gastos

Aplicativo Android em Kotlin, Jetpack Compose e Room. Os dados ficam no dispositivo.

## Importar os gastos desta conversa

1. Transfira `saidas_detalhadas_por_destinatario.csv` (ou a cópia `Gastos_importar.csv`) para o celular.
2. Abra **Gastos → Importar** e selecione o arquivo.
3. Confira quantidade, grupos, período e total e toque em **Importar lançamentos**.
4. Use **Todos os meses** para ver o período completo. A busca encontra grupos, descrições e formas de pagamento. Toque em um grupo para expandir os lançamentos.

A importação usa o CSV detalhado, não o resumo `saidas_por_destinatario.csv`: o detalhado preserva as datas, os IDs e as transações individuais. Os nomes de grupo já definidos no arquivo são mantidos. Dados bancários pessoais não estão incluídos no código ou no APK.

### Formatos aceitos

- CSV detalhado da conversa, UTF-8 com ou sem BOM, separado por ponto e vírgula:
  `Data;Grupo;Tipo;Saida (R$);Descricao original;ID;Origem`
- CSV exportado pelo Gastos, incluindo o formato antigo separado por vírgula:
  `ID,Categoria,Valor,Descricao,Data`
- Valores como `137,40`, `1.234,56` ou `137.40`; datas `dd/MM/aaaa` e `dd/MM/aaaa HH:mm`.
- Campos entre aspas podem conter separadores, quebras de linha e aspas duplicadas.

O arquivo inteiro é validado antes de salvar. Valores inválidos, datas ausentes e linhas incompletas causam um erro visível; nenhuma parte do arquivo é salva. A gravação em lote é uma transação. Arquivos são limitados a 5 milhões de caracteres e 50 mil lançamentos.

IDs externos têm um índice único: importar novamente os mesmos lançamentos não cria cópias. IDs repetidos com dados divergentes no mesmo arquivo são rejeitados. Formatos antigos sem ID externo recebem uma identidade calculada; ocorrências idênticas legítimas no mesmo arquivo são preservadas. Importações nunca substituem nem excluem gastos existentes.

**Exportar todos os lançamentos**, no menu superior, gera um CSV compatível com o importador. A origem manual é separada da forma de pagamento: um lançamento manual pode ser Pix e continua identificado como manual.

## Organizar grupos

- No cartão de um grupo, abra **⋮ → Alterar nome do grupo**. A alteração inclui os gastos de todos os meses, mesmo com um filtro ativo. Escolher um nome que já existe reúne os grupos, após confirmação.
- Toque em **Selecionar gastos**, marque os lançamentos e use **Mover para grupo**. Digite um nome novo, como `LAZER`, ou escolha um grupo existente.
- A seleção é mantida ao trocar a busca e o mês. **Marcar visíveis** acrescenta os resultados atuais à seleção; **Limpar seleção** remove todas as marcações.
- As operações alteram apenas o grupo: valores, datas, descrições, formas de pagamento e IDs de importação são preservados. Reimportar o mesmo arquivo não desfaz a organização.

## Desenvolvimento

Abra esta pasta no Android Studio. Requer Android SDK 36.1 e JDK 21 para o Gradle.

Configure um aplicativo Android no seu projeto Firebase com o identificador `com.example.gastos` e coloque o arquivo `google-services.json` em `app/`. Esse arquivo de configuração local não é versionado. O prompt pessoal de conversão de extratos e arquivos financeiros também ficam fora do repositório.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Para validar um CSV privado local com os totais esperados desta conversa, sem copiá-lo para o projeto:

```sh
GASTOS_IMPORT_FIXTURE=/caminho/saidas_detalhadas_por_destinatario.csv ./gradlew :app:testDebugUnitTest --rerun-tasks
```

Os testes instrumentados verificam migração da base antiga sem perda de registros, rollback de importação, prévia e reimportação sem duplicatas. A base Room está na versão 2 e migra a versão 1 sem recriar tabelas.
