# Список 200 кімнатних рослин і порядок партій (TASK-045, на погодження Власнику)

> Статус: **ПОГОДЖЕНО Власником 2026-11-20 з виправленнями (ADR-019)**; виправлення внесено цим оновленням. Латинські назви звірено з GBIF (§3), українські назви вичитано (§3; потрібна вибіркова звірка за uk.wikipedia/садівничими довідниками).

## 1. Як складено

- Рішення Власника: додано Citrus limon, Laurus nobilis, Olea europaea, Persea americana, Ocimum basilicum, Salvia rosmarinus, Mentha; щоб лишилось 200, з B7 прибрано найменш поширені й дорогі колекційні (monstera_obliqua, anthurium_crystallinum, homalomena_rubescens, dionaea_muscipula, nepenthes_hybrid, stapelia, pachypodium_lamerei); партії перераховано по 25.
- 20 наявних рослин (їхні ID **не змінюються**) + 180 нових = 200. Дублі й синоніми прибрано (напр. *Philodendron selloum* = *P. bipinnatifidum*; *Dracaena deremensis* = *D. fragrans*; *Nolina recurvata* = *Beaucarnea recurvata*: у списку лишено один запис).
- Реальних даних про популярність (продажі, пошукові запити) у нас **немає**; порядок партій редакторський: легкі й поширені рослини раніше; примхливі й колекційні пізніше. Коли з'явиться сигнал попиту (1.1), порядок B3–B7 коригується за ним.
- Орієнтири складу (довідники університетських служб; сторінки знайдено пошуком 2026-11-20, повністю не перевірялись): [Iowa State](https://yardandgarden.extension.iastate.edu/how-to/easy-low-maintenance-houseplants); [Univ. of Maryland](https://extension.umd.edu/resource/selecting-indoor-plants); [Univ. of Nebraska–Lincoln G837](https://extensionpubs.unl.edu/publication/g837/na/html/view); [Colorado State](https://arapahoe.extension.colostate.edu/2024/01/05/popular-houseplants-for-beginners); [Oregon State](https://extension.oregonstate.edu/sites/default/files/documents/54611/five-easy-houseplants-worth-trying.pdf); перелік ≈200 кімнатних рослин університету Джорджії згадано в [Piedmont Master Gardeners](https://piedmontmastergardeners.org/?p=39979). Повний список не копіювався з жодного з них.

## 2. Партії

Мінімум для релізу 1.1: B0–B5 (150 карток); B6–B7 у 1.2 (рішення Власника Q-1.1-6). Колонка «GBIF» = результат звірки назви з GBIF Backbone Taxonomy (§3).

### B0: 20 наявних + 5 нових (переписати на новий формат, 4 мови)

| # | ID | Латинська назва | Українська назва | Група | Причина | GBIF | Укр. назва |
|---|---|---|---|---|---|---|---|
| 1 | `zamioculcas` (наявний) | *Zamioculcas zamiifolia* | Заміокулькас | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 2 | `sansevieria` (наявний) | *Dracaena trifasciata* | Сансевієрія | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ⚠ medium: Dracaena trifasciata; назва «Сансевієрія» - побутова, усталена |
| 3 | `epipremnum` (наявний) | *Epipremnum aureum* | Епіпремнум золотистий | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 4 | `monstera` (наявний) | *Monstera deliciosa* | Монстера делікатесна | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ⚠ medium: M. deliciosa: також «Монстера чарівна»; «делікатесна» поширена |
| 5 | `spathiphyllum` (наявний) | *Spathiphyllum wallisii* | Спатифілум | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 6 | `chlorophytum` (наявний) | *Chlorophytum comosum* | Хлорофітум чубатий | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 7 | `crassula` (наявний) | *Crassula ovata* | Красула (товстянка) | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 8 | `ficus_elastica` (наявний) | *Ficus elastica* | Фікус каучуконосний | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 9 | `ficus_benjamina` (наявний) | *Ficus benjamina* | Фікус Бенджаміна | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 10 | `dracaena_marginata` (наявний) | *Dracaena marginata* | Драцена облямована | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 11 | `peperomia` (наявний) | *Peperomia obtusifolia* | Пеперомія туполиста | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 12 | `aglaonema` (наявний) | *Aglaonema commutatum* | Аглаонема | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 13 | `philodendron` (наявний) | *Philodendron hederaceum* | Філодендрон лазячий | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 14 | `syngonium` (наявний) | *Syngonium podophyllum* | Сингоніум | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 15 | `aloe_vera` (наявний) | *Aloe vera* | Алое вера | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 16 | `tradescantia` (наявний) | *Tradescantia zebrina* | Традесканція зебрина | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 17 | `maranta` (наявний) | *Maranta leuconeura* | Маранта триколірна | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ⚠ medium (наявна назва; пропозиція: Маранта біложилкова): M. leuconeura - біложилкова; «триколірна» лише сорт kerchoveana 'Tricolor' |
| 18 | `schefflera` (наявний) | *Schefflera arboricola* | Шефлера деревоподібна | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 19 | `calathea` (наявний) | *Goeppertia makoyana* | Калатея Макоя | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 20 | `begonia` (наявний) | *Begonia maculata* | Бегонія плямиста | наявні | наявна в застосунку (ID зберігається) | ✓ прийнята | ✓ |
| 21 | `pilea_peperomioides` | *Pilea peperomioides* | Пілея пеперомієвидна | листяні | дуже популярна в соцмережах, проста, компактна | ✓ прийнята | ✓ |
| 22 | `phalaenopsis_hybrid` | *Phalaenopsis* | Фаленопсис (гібриди) | орхідеї | найпопулярніша кімнатна орхідея, часті запити про догляд | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ✓ |
| 23 | `ficus_lyrata` | *Ficus lyrata* | Фікус ліроподібний | дерева | модна велика рослина, часті питання про листопад | ✓ прийнята | ✓ |
| 24 | `hoya_carnosa` | *Hoya carnosa* | Хойя м'ясиста | ліани | популярна квітуча ліана, легка | ✓ прийнята | ✓ |
| 25 | `nephrolepis_exaltata` | *Nephrolepis exaltata* | Нефролепіс | папороті | найпоширеніша кімнатна папороть | ✓ прийнята | ✓ |

### B1: найпоширеніші (листяні, дерева, ліани, кухонні трави)

| # | ID | Латинська назва | Українська назва | Група | Причина | GBIF | Укр. назва |
|---|---|---|---|---|---|---|---|
| 1 | `citrus_limon` | *Citrus limon* | Лимон кімнатний | дерева | дуже популярний в Україні й Польщі, плодоносить | ✓ прийнята | ✓ |
| 2 | `ocimum_basilicum` | *Ocimum basilicum* | Базилік | трави | найпопулярніша кухонна трава на підвіконні | ✓ прийнята | ✓ |
| 3 | `salvia_rosmarinus` | *Salvia rosmarinus* | Розмарин | трави | популярна кухонна трава, примхлива до поливу | ✓ прийнята | ✓ |
| 4 | `mentha` | *Mentha* | М'ята | трави | кухонна трава (картка на рівні роду) | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ✓ |
| 5 | `dracaena_fragrans` | *Dracaena fragrans* | Драцена запашна | дерева | дуже поширена, витримує тінь | ✓ прийнята | ✓ |
| 6 | `dracaena_reflexa` | *Dracaena reflexa* | Драцена загнута (пісня Індії) | дерева | поширена офісна рослина | ✓ прийнята | ⚠ medium: «загнута» вживається, також «відігнута»; «пісня Індії» - сорт |
| 7 | `dracaena_sanderiana` | *Dracaena sanderiana* | Драцена Сандера (щасливий бамбук) | дерева | продається всюди, часто в воді | ✓ прийнята | ✓ |
| 8 | `cyperus_alternifolius` | *Cyperus alternifolius* | Циперус черговолистий (сить) | листяні | поширений, любить вологу | ✓ прийнята | ⚠ medium: alternifolius = черговолистий; «сит'є» - збірна форма, рослина - сить |
| 9 | `yucca_gigantea` | *Yucca gigantea* | Юка гігантська | дерева | дуже поширена, невибаглива | ✓ прийнята | ✓ |
| 10 | `pachira_aquatica` | *Pachira aquatica* | Пахіра водна (грошове дерево) | дерева | популярний подарунок | ✓ прийнята | ✓ |
| 11 | `beaucarnea_recurvata` | *Beaucarnea recurvata* | Бокарнея відігнута (пляшкова пальма) | дерева | популярна, толерантна до пересушування | ✓ прийнята | ⚠ medium: було «Бокарнея вигнута (пляшкова пальма)»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 12 | `aspidistra_elatior` | *Aspidistra elatior* | Аспідистра висока | листяні | стійка до тіні, класика | ✓ прийнята | ✓ |
| 13 | `monstera_adansonii` | *Monstera adansonii* | Монстера Адансона | ліани | популярна ліана, часті запити | ✓ прийнята | ✓ |
| 14 | `scindapsus_pictus` | *Scindapsus pictus* | Сциндапсус строкатий | ліани | популярна, схожа на епіпремнум | ✓ прийнята | ⚠ medium: «строкатий» - поширена торгова назва; буквально pictus - розфарбований |
| 15 | `rhaphidophora_tetrasperma` | *Rhaphidophora tetrasperma* | Рафідофора чотиринасінна (міні-монстера) | ліани | популярна в соцмережах | ✓ прийнята | ⚠ medium: правопис числівникової основи: чотиринасінна |
| 16 | `philodendron_bipinnatifidum` | *Philodendron bipinnatifidum* | Філодендрон перисторозсічений (ксанаду) | листяні | велика декоративна | ✓ прийнята | ✓ |
| 17 | `philodendron_melanochrysum` | *Philodendron melanochrysum* | Філодендрон меланохрізум (чорне золото) | ліани | колекційна, популярна | ✓ прийнята | ⚠ medium: усталена в колекціонерів транслітерація виду; «чорне золото» - торгова назва |
| 18 | `dieffenbachia_seguine` | *Dieffenbachia seguine* | Дифенбахія | листяні | дуже поширена, токсична, потрібна обережність | ✓ прийнята | ✓ |
| 19 | `alocasia_amazonica` | *Alocasia amazonica* | Алоказія амазонська | листяні | популярна, примхлива, багато питань | ⚠ синонім у GBIF → Alocasia ×mortfontanensis André; лишаємо поширену назву, прийняту додати в aliases | ✓ |
| 20 | `anthurium_andraeanum` | *Anthurium andraeanum* | Антуріум Андре | квітучі | поширений подарунок, квітне | ✓ прийнята | ✓ |
| 21 | `hedera_helix` | *Hedera helix* | Плющ звичайний | ліани | класика, токсичний для тварин | ✓ прийнята | ✓ |
| 22 | `cissus_rhombifolia` | *Cissus rhombifolia* | Цисус ромболистий | ліани | легка, популярна | ⚠ синонім у GBIF → Cissus alata Jacq.; лишаємо поширену назву, прийняту додати в aliases | ✓ |
| 23 | `asparagus_setaceus` | *Asparagus setaceus* | Аспарагус перистий | листяні | поширений | ✓ прийнята | ✓ |
| 24 | `asparagus_densiflorus` | *Asparagus densiflorus* | Аспарагус Шпренгера | листяні | поширений, токсичний | ✓ прийнята | ⚠ medium: «Шпренгера» - назва сорту 'Sprengeri', на практиці вживана |
| 25 | `fatsia_japonica` | *Fatsia japonica* | Фатсія японська | дерева | витримує прохолоду | ✓ прийнята | ✓ |

### B2: дерева й пальми (лавр, олива, авокадо, фікуси)

| # | ID | Латинська назва | Українська назва | Група | Причина | GBIF | Укр. назва |
|---|---|---|---|---|---|---|---|
| 1 | `cordyline_fruticosa` | *Cordyline fruticosa* | Кордиліна | дерева | яскраве листя | ✓ прийнята | ✓ |
| 2 | `codiaeum_variegatum` | *Codiaeum variegatum* | Кодіеум (кротон) | листяні | яскрава, примхлива | ✓ прийнята | ⚠ medium: у ботанічних довідниках рід Codiaeum - кодіеум; кротон - побутова назва |
| 3 | `fittonia_albivenis` | *Fittonia albivenis* | Фітонія | листяні | популярна міні-рослина | ✓ прийнята | ✓ |
| 4 | `hypoestes_phyllostachya` | *Hypoestes phyllostachya* | Гіпестес (рослина-веснянка) | листяні | поширена | ✓ прийнята | ⚠ medium: «рослина-веснянка» - побутова, ок |
| 5 | `laurus_nobilis` | *Laurus nobilis* | Лавр благородний | дерева | популярний у кімнатній культурі, кухонне листя | ✓ прийнята | ⚠ medium: було «Лавр шляхетний»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 6 | `olea_europaea` | *Olea europaea* | Маслина європейська (олива) | дерева | популярне дерево в Польщі й Україні | ✓ прийнята | ⚠ medium: було «Олива європейська (оливкове дерево)»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 7 | `persea_americana` | *Persea americana* | Авокадо (з кісточки) | дерева | дуже популярне вирощування з кісточки | ✓ прийнята | ✓ |
| 8 | `howea_forsteriana` | *Howea forsteriana* | Ховея Форстера (кентія) | пальми | найпоширеніша кімнатна пальма | ✓ прийнята | ✓ |
| 9 | `chamaedorea_elegans` | *Chamaedorea elegans* | Хамедорея витончена | пальми | компактна, безпечна для тварин за джерелами | ✓ прийнята | ✓ |
| 10 | `dypsis_lutescens` | *Dypsis lutescens* | Дипсис жовтуватий (арека) | пальми | дуже поширена | ✓ прийнята | ⚠ medium: було «Діпсис жовтуватий (арека)»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 11 | `rhapis_excelsa` | *Rhapis excelsa* | Рапіс високий | пальми | стійка, повільна | ✓ прийнята | ✓ |
| 12 | `phoenix_roebelenii` | *Phoenix roebelenii* | Фінік Робелена | пальми | поширена декоративна | ✓ прийнята | ✓ |
| 13 | `livistona_chinensis` | *Livistona chinensis* | Лівістона китайська | пальми | поширена | ✓ прийнята | ✓ |
| 14 | `cycas_revoluta` | *Cycas revoluta* | Саговник відгорнений (цикас) | пальми | токсичний, потрібні застереження | ✓ прийнята | ⚠ medium: було «Цикас завитий (саговник)»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 15 | `strelitzia_reginae` | *Strelitzia reginae* | Стрелітція королівська | дерева | популярна квітуча | ✓ прийнята | ✓ |
| 16 | `strelitzia_nicolai` | *Strelitzia nicolai* | Стрелітція Ніколая | дерева | велика декоративна | ✓ прийнята | ✓ |
| 17 | `musa_acuminata` | *Musa acuminata* | Банан кімнатний | дерева | часті запити | ✓ прийнята | ⚠ medium: Musa acuminata - «банан загострений»; кімнатний банан - зазвичай сорт 'Dwarf Cavendish'; назва узагальнена |
| 18 | `ficus_microcarpa` | *Ficus microcarpa* | Фікус мікрокарпа (женьшень) | дерева | популярний бонсай-подібний | ✓ прийнята | ⚠ medium: мікрокарпа - поширена транслітерація, також «дрібноплідний» |
| 19 | `ficus_pumila` | *Ficus pumila* | Фікус карликовий (повзучий) | ліани | поширений | ✓ прийнята | ⚠ medium: побутово «карликовий», у довідниках також «повзучий» |
| 20 | `ficus_binnendijkii` | *Ficus binnendijkii* | Фікус Біннендійка (алі) | дерева | поширений | ✓ прийнята | ⚠ medium: прізвище Binnendijk - Біннендійк (подвоєне н) |
| 21 | `ficus_benghalensis` | *Ficus benghalensis* | Фікус бенгальський | дерева | поширений | ✓ прийнята | ✓ |
| 22 | `schefflera_actinophylla` | *Schefflera actinophylla* | Шефлера промениста | дерева | поширена | ⚠ синонім у GBIF → Heptapleurum actinophyllum (Endl.) Lowry & G.M.Plunkett; лишаємо поширену назву, прийняту додати в aliases | ✓ |
| 23 | `polyscias_fruticosa` | *Polyscias fruticosa* | Полісціас | дерева | декоративний | ✓ прийнята | ⚠ medium: було «Поліціас»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 24 | `radermachera_sinica` | *Radermachera sinica* | Радермахера китайська | дерева | поширена | ✓ прийнята | ✓ |
| 25 | `murraya_paniculata` | *Murraya paniculata* | Муррая волотиста | дерева | квітуча, ароматна | ✓ прийнята | ⚠ medium: uk.wikipedia: рід Муррая; paniculata - волотиста |

### B3: пальми, дерева, сукуленти (після перерозподілу)

| # | ID | Латинська назва | Українська назва | Група | Причина | GBIF | Укр. назва |
|---|---|---|---|---|---|---|---|
| 1 | `citrus_x_microcarpa` | *Citrus × microcarpa* | Каламондин (кімнатний цитрус) | дерева | дуже популярний, плодоносить | ✓ прийнята | ✓ |
| 2 | `coffea_arabica` | *Coffea arabica* | Кавове дерево | дерева | популярне | ✓ прийнята | ✓ |
| 3 | `tetrastigma_voinierianum` | *Tetrastigma voinierianum* | Тетрастигма | ліани | велика ліана | ✓ прийнята | ✓ |
| 4 | `cissus_antarctica` | *Cissus antarctica* | Цисус антарктичний | ліани | поширена | ✓ прийнята | ✓ |
| 5 | `chamaedorea_seifrizii` | *Chamaedorea seifrizii* | Хамедорея Сейфріца (бамбукова пальма) | пальми | поширена | ✓ прийнята | ✓ |
| 6 | `dracaena_surculosa` | *Dracaena surculosa* | Драцена крапчаста | дерева | поширена | ✓ прийнята | ⚠ medium: також «золотий пил»; «крапчаста» вживається |
| 7 | `ravenea_rivularis` | *Ravenea rivularis* | Равенея | пальми | поширена | ✓ прийнята | ✓ |
| 8 | `echeveria_elegans` | *Echeveria elegans* | Ечеверія витончена | сукуленти | дуже популярна | ✓ прийнята | ⚠ medium: було «Ехеверія витончена»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 9 | `haworthiopsis_attenuata` | *Haworthiopsis attenuata* | Гавортія смугаста | сукуленти | дуже популярна | ✓ прийнята | ⚠ medium: рід Haworthiopsis часто продається як «Гавортія» (також Хавортія) |
| 10 | `haworthia_cooperi` | *Haworthia cooperi* | Гавортія Купера | сукуленти | популярна | ✓ прийнята | ✓ |
| 11 | `aloe_aristata` | *Aloe aristata* | Алое остисте | сукуленти | поширене | ⚠ синонім у GBIF → Aristaloe aristata (Haw.) Boatwr. & J.C.Manning; лишаємо поширену назву, прийняту додати в aliases | ✓ |
| 12 | `aloe_arborescens` | *Aloe arborescens* | Алое деревоподібне | сукуленти | поширене | ✓ прийнята | ✓ |
| 13 | `kalanchoe_blossfeldiana` | *Kalanchoe blossfeldiana* | Каланхое Блоссфельда | сукуленти | поширена квітуча | ✓ прийнята | ⚠ medium: було «Каланхое Блосфельда»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 14 | `kalanchoe_daigremontiana` | *Kalanchoe daigremontiana* | Каланхое Дайгремонта | сукуленти | поширена | ✓ прийнята | ⚠ medium: було «Каланхое Дегремона»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 15 | `kalanchoe_tomentosa` | *Kalanchoe tomentosa* | Каланхое повстисте | сукуленти | популярна | ✓ прийнята | ✓ |
| 16 | `sedum_morganianum` | *Sedum morganianum* | Седум Моргана (ослячий хвіст) | сукуленти | популярна підвісна | ✓ прийнята | ⚠ medium: український рід Sedum - очиток; «седум» вживається в садівництві |
| 17 | `sedum_rubrotinctum` | *Sedum rubrotinctum* | Седум червонуватий | сукуленти | поширений | ✓ прийнята | ⚠ medium: побутово «седум/очиток»; rubrotinctum - червонобарвний |
| 18 | `sempervivum_tectorum` | *Sempervivum tectorum* | Молодило покрівельне | сукуленти | поширене | ✓ прийнята | ✓ |
| 19 | `lithops` | *Lithops* | Літопси | сукуленти | цікаві, складний полив | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ✓ |
| 20 | `graptopetalum_paraguayense` | *Graptopetalum paraguayense* | Граптопеталум | сукуленти | поширений | ✓ прийнята | ✓ |
| 21 | `curio_rowleyanus` | *Curio rowleyanus* | Сенеціо Роулі (нитка перлів) | сукуленти | популярна підвісна | ✓ прийнята | ✓ |
| 22 | `curio_radicans` | *Curio radicans* | Сенеціо повзучий (банановий ланцюжок) | сукуленти | популярна підвісна | ✓ прийнята | ⚠ medium: замість «Кліня»; Senecio radicans - сенеціо; повзучий - за habit |
| 23 | `portulacaria_afra` | *Portulacaria afra* | Портулакарія африканська | сукуленти | поширена | ✓ прийнята | ✓ |
| 24 | `aeonium_arboreum` | *Aeonium arboreum* | Еоніум деревоподібний | сукуленти | поширений | ✓ прийнята | ✓ |
| 25 | `crassula_perforata` | *Crassula perforata* | Красула пронизана | сукуленти | поширена | ✓ прийнята | ✓ |

### B4: квітучі

| # | ID | Латинська назва | Українська назва | Група | Причина | GBIF | Укр. назва |
|---|---|---|---|---|---|---|---|
| 1 | `crassula_arborescens` | *Crassula arborescens* | Красула деревоподібна | сукуленти | поширена | ✓ прийнята | ✓ |
| 2 | `ceropegia_woodii` | *Ceropegia woodii* | Церопегія Вуда | сукуленти | популярна підвісна | ✓ прийнята | ✓ |
| 3 | `hoya_kerrii` | *Hoya kerrii* | Хойя Керрі (серце) | сукуленти | подарунок на день закоханих | ✓ прийнята | ✓ |
| 4 | `euphorbia_trigona` | *Euphorbia trigona* | Молочай тригранний | сукуленти | поширений, токсичний сік | ✓ прийнята | ✓ |
| 5 | `euphorbia_milii` | *Euphorbia milii* | Молочай Міля | сукуленти | поширений, токсичний сік | ✓ прийнята | ⚠ medium: було «Молочай Мілля»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 6 | `euphorbia_pulcherrima` | *Euphorbia pulcherrima* | Пуансетія (різдвяна зірка) | квітучі | сезонно дуже популярна | ✓ прийнята | ✓ |
| 7 | `adenium_obesum` | *Adenium obesum* | Аденіум | сукуленти | квітуча, токсична | ✓ прийнята | ✓ |
| 8 | `saintpaulia_ionantha` | *Saintpaulia ionantha* | Сенполія (узамбарська фіалка) | квітучі | одна з найпопулярніших | ⚠ синонім у GBIF → Streptocarpus ionanthus subsp. ionanthus; лишаємо поширену назву, прийняту додати в aliases | ⚠ medium: було «Сентполія (узамбарська фіалка)»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 9 | `streptocarpus` | *Streptocarpus* | Стрептокарпус | квітучі | популярний | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г); GBIF не знаходить збігу на рівні роду | ✓ |
| 10 | `sinningia_speciosa` | *Sinningia speciosa* | Глоксинія | квітучі | поширена | ✓ прийнята | ✓ |
| 11 | `begonia_rex` | *Begonia rex* | Бегонія королівська | листяні | поширена | ✓ прийнята | ✓ |
| 12 | `begonia_semperflorens` | *Begonia semperflorens* | Бегонія вічноквітуча | квітучі | поширена | ✓ прийнята | ✓ |
| 13 | `hibiscus_rosa_sinensis` | *Hibiscus rosa-sinensis* | Гібіскус китайський | квітучі | дуже популярний | ✓ прийнята | ✓ |
| 14 | `pelargonium_zonale` | *Pelargonium zonale* | Пеларгонія зональна (герань) | квітучі | дуже популярна | ✓ прийнята | ✓ |
| 15 | `cyclamen_persicum` | *Cyclamen persicum* | Цикламен перський | квітучі | сезонний, токсичний | ✓ прийнята | ⚠ medium: було «Цикламен персидський»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 16 | `clivia_miniata` | *Clivia miniata* | Клівія | квітучі | популярна | ✓ прийнята | ✓ |
| 17 | `hippeastrum_hybrid` | *Hippeastrum* | Гіпеаструм (амариліс) | квітучі | сезонно популярний, токсичний | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ⚠ medium: було «Гіппеаструм (амариліс)»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 18 | `gardenia_jasminoides` | *Gardenia jasminoides* | Гарденія | квітучі | популярна, примхлива | ✓ прийнята | ✓ |
| 19 | `jasminum_polyanthum` | *Jasminum polyanthum* | Жасмин багатоквітковий | квітучі | поширений | ✓ прийнята | ✓ |
| 20 | `rhododendron_simsii` | *Rhododendron simsii* | Азалія кімнатна | квітучі | сезонна | ✓ прийнята | ✓ |
| 21 | `impatiens_walleriana` | *Impatiens walleriana* | Бальзамін (розрив-трава) Воллера | квітучі | поширений | ✓ прийнята | ⚠ medium: було «Бальзамін Уоллера»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 22 | `abutilon` | *Abutilon* | Абутилон (кімнатний клен) | квітучі | поширений | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ⚠ medium: було «Абутілон (кімнатний клен)»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 23 | `fuchsia_hybrida` | *Fuchsia* | Фуксія | квітучі | поширена | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ✓ |
| 24 | `bougainvillea_glabra` | *Bougainvillea glabra* | Бугенвілія | квітучі | популярна | ✓ прийнята | ✓ |
| 25 | `crossandra_infundibuliformis` | *Crossandra infundibuliformis* | Кросандра | квітучі | поширена | ✓ прийнята | ⚠ medium: було «Кроссандра»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |

### B5: марантові/калатеї, папороті, підвісні й дрібнолисті

| # | ID | Латинська назва | Українська назва | Група | Причина | GBIF | Укр. назва |
|---|---|---|---|---|---|---|---|
| 1 | `justicia_brandegeeana` | *Justicia brandegeeana* | Юстиція (креветкова рослина) | квітучі | поширена | ✓ прийнята | ✓ |
| 2 | `pachystachys_lutea` | *Pachystachys lutea* | Пахістахіс жовтий | квітучі | поширений | ✓ прийнята | ✓ |
| 3 | `aphelandra_squarrosa` | *Aphelandra squarrosa* | Афеляндра | квітучі | поширена | ✓ прийнята | ⚠ medium: було «Афеландра»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 4 | `episcia_cupreata` | *Episcia cupreata* | Епісція | квітучі | популярна | ✓ прийнята | ✓ |
| 5 | `aeschynanthus_radicans` | *Aeschynanthus radicans* | Есхінантус | квітучі | підвісна, популярний | ✓ прийнята | ✓ |
| 6 | `schlumbergera_truncata` | *Schlumbergera truncata* | Шлюмбергера (декабрист) | квітучі | дуже популярна | ✓ прийнята | ✓ |
| 7 | `hatiora_gaertneri` | *Hatiora gaertneri* | Хатіора (великодній кактус) | квітучі | популярна | ✓ прийнята | ✓ |
| 8 | `goeppertia_orbifolia` | *Goeppertia orbifolia* | Калатея круглолиста | марантові | дуже популярна, примхлива | ✓ прийнята | ⚠ medium: орбіфолія - також «кругла»; вживане |
| 9 | `goeppertia_lancifolia` | *Goeppertia lancifolia* | Калатея списоподібна | марантові | поширена | ⚠ синонім у GBIF → Goeppertia insignis (W.Bull ex W.E.Marshall) J.M.A.Braga, L.J.T.Cardoso & R.Couto; лишаємо поширену назву, прийняту додати в aliases | ✓ |
| 10 | `goeppertia_ornata` | *Goeppertia ornata* | Калатея прикрашена | марантові | поширена | ✓ прийнята | ⚠ medium: ornata - прикрашена; вживаність помірна |
| 11 | `goeppertia_roseopicta` | *Goeppertia roseopicta* | Калатея розеопікта | марантові | популярна | ✓ прийнята | ⚠ medium: «рожево-фарбована» - невстановлена калька; використано транслітерований вид |
| 12 | `goeppertia_zebrina` | *Goeppertia zebrina* | Калатея зебрина | марантові | поширена | ✓ прийнята | ⚠ medium: у магазинах/довідниках - «зебрина» |
| 13 | `ctenanthe_setosa` | *Ctenanthe setosa* | Ктенанта щетиниста | марантові | поширена | ✓ прийнята | ✓ |
| 14 | `stromanthe_sanguinea` | *Stromanthe sanguinea* | Строманта | марантові | поширена | ⚠ синонім у GBIF → Stromanthe thalia (Vell.) J.M.A.Braga; лишаємо поширену назву, прийняту додати в aliases | ✓ |
| 15 | `asplenium_nidus` | *Asplenium nidus* | Аспленій гніздовий | папороті | дуже популярна | ✓ прийнята | ⚠ medium: було «Асплений гніздоподібний»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 16 | `adiantum_raddianum` | *Adiantum raddianum* | Адіантум Радді | папороті | популярна | ✓ прийнята | ✓ |
| 17 | `platycerium_bifurcatum` | *Platycerium bifurcatum* | Платицеріум (оленячі роги) | папороті | популярна колекційна | ✓ прийнята | ✓ |
| 18 | `pteris_cretica` | *Pteris cretica* | Птерис критський | папороті | поширена | ✓ прийнята | ✓ |
| 19 | `cyrtomium_falcatum` | *Cyrtomium falcatum* | Циртоміум | папороті | стійка | ✓ прийнята | ✓ |
| 20 | `blechnum_gibbum` | *Blechnum gibbum* | Блехнум | папороті | поширений | ⚠ синонім у GBIF → Oceaniopteris gibba (Labill.) Gasper & Salino; лишаємо поширену назву, прийняту додати в aliases | ✓ |
| 21 | `davallia_canariensis` | *Davallia canariensis* | Давалія | папороті | поширена | ✓ прийнята | ✓ |
| 22 | `selaginella_kraussiana` | *Selaginella kraussiana* | Селагінела (плаунок) | папороті | поширена | ✓ прийнята | ⚠ medium: було «Селагінелла»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 23 | `tradescantia_spathacea` | *Tradescantia spathacea* | Традесканція човникоподібна | листяні | поширена | ✓ прийнята | ✓ |
| 24 | `tradescantia_pallida` | *Tradescantia pallida* | Традесканція бліда (сетекреазія пурпурова) | листяні | поширена | ✓ прийнята | ⚠ medium: pallida - бліда; побутово сетекреазія пурпурова |
| 25 | `callisia_repens` | *Callisia repens* | Калісія повзуча | листяні | поширена | ✓ прийнята | ✓ |

### B6: бромелієві, орхідеї, кактуси, дрібні (у 1.2)

| # | ID | Латинська назва | Українська назва | Група | Причина | GBIF | Укр. назва |
|---|---|---|---|---|---|---|---|
| 1 | `plectranthus_scutellarioides` | *Plectranthus scutellarioides* | Колеус | листяні | поширений | ⚠ синонім у GBIF → Coleus scutellarioides (L.) Benth.; лишаємо поширену назву, прийняту додати в aliases | ✓ |
| 2 | `plectranthus_verticillatus` | *Plectranthus verticillatus* | Плектрантус (шведський плющ) | листяні | поширений | ✓ прийнята | ✓ |
| 3 | `gynura_aurantiaca` | *Gynura aurantiaca* | Гінура | листяні | поширена | ✓ прийнята | ✓ |
| 4 | `pilea_cadierei` | *Pilea cadierei* | Пілея Кадьє | листяні | поширена | ✓ прийнята | ✓ |
| 5 | `pilea_microphylla` | *Pilea microphylla* | Пілея дрібнолиста | листяні | поширена | ✓ прийнята | ✓ |
| 6 | `peperomia_argyreia` | *Peperomia argyreia* | Пеперомія срібляста (кавун) | листяні | дуже популярна | ✓ прийнята | ⚠ medium: назва узгоджена; «кавун» - побутова |
| 7 | `peperomia_caperata` | *Peperomia caperata* | Пеперомія зморшкувата | листяні | поширена | ✓ прийнята | ✓ |
| 8 | `guzmania_lingulata` | *Guzmania lingulata* | Гузманія | бромелієві | дуже популярна | ✓ прийнята | ✓ |
| 9 | `aechmea_fasciata` | *Aechmea fasciata* | Ехмея смугаста | бромелієві | популярна | ✓ прийнята | ✓ |
| 10 | `vriesea_splendens` | *Vriesea splendens* | Фрізея (вріезія) | бромелієві | популярна | ⚠ синонім у GBIF → Lutheria splendens (Brongn.) Barfuss & W.Till; лишаємо поширену назву, прийняту додати в aliases | ⚠ medium: було «Вріезія блискуча»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 11 | `tillandsia_ionantha` | *Tillandsia ionantha* | Тиландсія іонанта | бромелієві | повітряна рослина, популярна | ✓ прийнята | ⚠ medium: було «Тілландсія іонанта»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 12 | `tillandsia_cyanea` | *Tillandsia cyanea* | Тиландсія синя | бромелієві | поширена | ✓ прийнята | ⚠ medium: було «Тілландсія синя»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 13 | `neoregelia_carolinae` | *Neoregelia carolinae* | Неорегелія | бромелієві | поширена | ✓ прийнята | ✓ |
| 14 | `cymbidium_hybrid` | *Cymbidium* | Цимбідіум | орхідеї | популярний | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ✓ |
| 15 | `dendrobium_nobile` | *Dendrobium nobile* | Дендробіум шляхетний | орхідеї | популярний | ✓ прийнята | ✓ |
| 16 | `paphiopedilum` | *Paphiopedilum* | Пафіопедилум (венерин черевичок) | орхідеї | популярний | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ✓ |
| 17 | `oncidium_hybrid` | *Oncidium* | Онцидіум | орхідеї | поширений | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ✓ |
| 18 | `epiphyllum_oxypetalum` | *Epiphyllum oxypetalum* | Епіфілум | кактуси | популярний | ✓ прийнята | ✓ |
| 19 | `rhipsalis_baccifera` | *Rhipsalis baccifera* | Рипсаліс | кактуси | популярний | ✓ прийнята | ⚠ medium: було «Ріпсаліс»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 20 | `opuntia_microdasys` | *Opuntia microdasys* | Опунція дрібноглохідна | кактуси | дуже поширена | ✓ прийнята | ❓ low: «дрібноглоста» сумнівне; microdasys = з дрібними глохідіями; усталеної назви не знайдено, можна «Опунція мікродазіс» |
| 21 | `mammillaria_elongata` | *Mammillaria elongata* | Маммілярія подовжена | кактуси | поширена | ✓ прийнята | ✓ |
| 22 | `echinocactus_grusonii` | *Echinocactus grusonii* | Ехінокактус Грузона | кактуси | поширений | ⚠ синонім у GBIF → Kroenleinia grusonii (Hildm.) Lodé; лишаємо поширену назву, прийняту додати в aliases | ✓ |
| 23 | `gymnocalycium_mihanovichii` | *Gymnocalycium mihanovichii* | Гімнокаліціум Міхановича | кактуси | поширений | ✓ прийнята | ✓ |
| 24 | `cereus_repandus` | *Cereus repandus* | Цереус перуанський | кактуси | поширений | ✓ прийнята | ⚠ medium: прибрано технічну дужку; C. repandus часто продається як C. peruvianus |
| 25 | `echinopsis` | *Echinopsis* | Ехінопсис | кактуси | квітучий | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ✓ |

### B7: колекційні й рідші (у 1.2)

| # | ID | Латинська назва | Українська назва | Група | Причина | GBIF | Укр. назва |
|---|---|---|---|---|---|---|---|
| 1 | `rebutia` | *Rebutia* | Ребуція | кактуси | квітуча | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ✓ |
| 2 | `cleistocactus_strausii` | *Cleistocactus strausii* | Клейстокактус Штрауса | кактуси | поширений | ⚠ синонім у GBIF → Borzicactus strausii (Heese) A.Berger; лишаємо поширену назву, прийняту додати в aliases | ✓ |
| 3 | `oxalis_triangularis` | *Oxalis triangularis* | Оксаліс трикутний | листяні | популярний | ✓ прийнята | ⚠ medium: українською також «Кислиця трикутна»; «оксаліс» поширено |
| 4 | `soleirolia_soleirolii` | *Soleirolia soleirolii* | Солейролія | листяні | поширена | ✓ прийнята | ✓ |
| 5 | `mimosa_pudica` | *Mimosa pudica* | Мімоза соромлива | листяні | цікава, популярна | ✓ прийнята | ⚠ medium: було «Мімоза сором'язлива»; виправлено за uk.wikipedia/правописом 2019 (QA PR #15) |
| 6 | `ardisia_crenata` | *Ardisia crenata* | Ардизія | дерева | плодоносить | ✓ прийнята | ✓ |
| 7 | `nertera_granadensis` | *Nertera granadensis* | Нертера | листяні | сезонна | ✓ прийнята | ✓ |
| 8 | `anthurium_scherzerianum` | *Anthurium scherzerianum* | Антуріум Шерцера | квітучі | поширений | ✓ прийнята | ✓ |
| 9 | `alocasia_zebrina` | *Alocasia zebrina* | Алоказія зебрина | листяні | поширена | ✓ прийнята | ✓ |
| 10 | `alocasia_macrorrhizos` | *Alocasia macrorrhizos* | Алоказія великокоренева | листяні | поширена | ✓ прийнята | ⚠ medium: великокоренева - калька macrorrhizos; усталеність помірна |
| 11 | `caladium_bicolor` | *Caladium bicolor* | Каладіум | листяні | сезонний | ✓ прийнята | ✓ |
| 12 | `colocasia_esculenta` | *Colocasia esculenta* | Колоказія | листяні | поширена | ✓ прийнята | ✓ |
| 13 | `philodendron_brandtianum` | *Philodendron brandtianum* | Філодендрон Брандта | ліани | поширений | ✓ прийнята | ✓ |
| 14 | `philodendron_erubescens` | *Philodendron erubescens* | Філодендрон рум'яний | ліани | поширений | ✓ прийнята | ✓ |
| 15 | `zamia_furfuracea` | *Zamia furfuracea* | Замія | пальми | поширена, токсична | ✓ прийнята | ✓ |
| 16 | `aglaonema_modestum` | *Aglaonema modestum* | Аглаонема скромна | листяні | поширена | ✓ прийнята | ✓ |
| 17 | `aglaonema_crispum` | *Aglaonema crispum* | Аглаонема хвиляста | листяні | поширена | ⚠ синонім у GBIF → Aglaonema robeleynii (Van Geert) Pitcher & Manda; лишаємо поширену назву, прийняту додати в aliases | ⚠ medium: crispum - кучерява/хвиляста; помірна усталеність |
| 18 | `epipremnum_pinnatum` | *Epipremnum pinnatum* | Епіпремнум перистий | ліани | поширений | ✓ прийнята | ✓ |
| 19 | `gasteria` | *Gasteria* | Гастерія | сукуленти | поширена | ❓ рівень роду/групи гібридів (картка на рівні роду, ADR-019 1г) | ✓ |
| 20 | `haworthia_retusa` | *Haworthia retusa* | Гавортія притуплена | сукуленти | поширена | ✓ прийнята | ⚠ medium: «притуплена» - переклад retusa; усталеність помірна |
| 21 | `agave_americana` | *Agave americana* | Агава американська | сукуленти | поширена | ✓ прийнята | ✓ |
| 22 | `euphorbia_tirucalli` | *Euphorbia tirucalli* | Молочай тирукаллі | сукуленти | поширений, токсичний сік | ✓ прийнята | ✓ |
| 23 | `curio_articulatus` | *Curio articulatus* | Сенеціо членистий (свічка) | сукуленти | поширений | ✓ прийнята | ⚠ medium: замість російської кальки «членистая»; Senecio articulatus |
| 24 | `pelargonium_peltatum` | *Pelargonium peltatum* | Пеларгонія плющелиста | квітучі | поширена | ✓ прийнята | ✓ |
| 25 | `dracaena_angolensis` | *Dracaena angolensis* | Драцена ангольська (сансевієрія циліндрична) | сукуленти | поширена, відрізняється від сансевієрії | ✓ прийнята | ✓ |

## 3. Звірка латинських назв (2026-11-20)

Метод: запит `species/match` до відкритого API GBIF для кожної з 200 назв. Результат: ✓ прийнята — 174; ⚠ синонім у GBIF — 13; ❓ рівень роду/групи гібридів — 13.
- Українські назви: вичитано субагентом **зі знань (вебджерела не відкривались)**; змінено 43 назв, 1 low-confidence, 57 medium; друга вичитка (QA, uk.wikipedia через Wikidata) виправила ще 23 назви за правописом 2019. Для 20 наявних рослин назви лишаються як у застосунку (пропозиції в колонці). **Потрібна вибіркова звірка за uk.wikipedia/садівничими довідниками перед записом у картки.**
- Записи рівня роду (Phalaenopsis, Cymbidium, Oncidium, Hippeastrum, Fuchsia, Streptocarpus, Gasteria, Mentha, Lithops, Abutilon, Paphiopedilum, Echinopsis, Rebutia) лишаються карткою на рівні роду (ADR-019 1г поширено на всі такі записи); ID збережено.
- Виправлено: *Alocasia macrorrhiza* → *A. macrorrhizos*; *Senecio articulatus* → *Curio articulatus*; *Pilea involucrata* (GBIF не знаходить) → *P. microphylla*; *Cereus peruvianus* → *C. repandus* (торгова назва *C. peruvianus* в aliases); прибрано дублі *Dracaena deremensis* і *Nolina recurvata*, замінені на *Cyperus alternifolius* і *Chamaedorea seifrizii*.
- «⚠ синонім»: GBIF Backbone відображає **таксономічну думку**, і для багатьох кімнатних рослин садівничі назви (*Saintpaulia*, *Echinocactus grusonii*, *Plectranthus scutellarioides*, *Vriesea splendens*) відрізняються від сучасних прийнятих. Пропозиція: **у картці `latin` = поширена садівнича назва; прийняту назву додавати в aliases і в `notes`**. Остаточно вирішує Власник/QA кожної партії (джерела догляду зазвичай використовують садівничу назву).
- «❓ рівень роду/групи»: запис означає рід або групу гібридів (*Streptocarpus*, *Hippeastrum*, *Fuchsia*, *Gasteria*, ін.). Для таких записів перед партією обираємо один репрезентативний вид/сорт або описуємо картку на рівні роду. Довідка: GBIF — не єдина авторитетна база; для фінальної звірки партії QA додатково дивиться POWO (powo.science.kew.org) для сумнівних назв.
- POWO окремо не запитувався: веб-інтерфейс не мав зручного API у цій сесії (**не перевірено**).

## 4. Що потрібно від Власника

Прочитати таблиці (≈ 5 хвилин) і відповісти: **погоджую / викреслити такі рослини / додати такі**; окремо — чи погоджуєте правило «садівнича назва в `latin`, прийнята GBIF в aliases/notes».
