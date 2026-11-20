# Список 200 кімнатних рослин і порядок партій (TASK-045, на погодження Власнику)

> Статус: **ПРОЄКТ НА ПОГОДЖЕННЯ** (партії не починаються до відповіді Власника). Дата: 2026-11-20. Чернетка Розробника; латинські назви ще мають пройти звірку з ботанічною базою (див. §3).

## 1. Як складено

- 20 наявних рослин (їхні ID **не змінюються**) + 180 нових = 200. Дублі й синоніми прибрано (напр. «Philodendron selloum» є синонімом *P. bipinnatifidum*, не включено окремо).
- Реальних даних про популярність (продажі, пошукові запити) у нас **немає**; порядок партій: редакторська оцінка поширеності й типових запитань початківців (легкі й поширені рослини раніше; примхливі й колекційні пізніше). Коли з'явиться сигнал попиту (1.1), порядок B3–B7 коригується за ним.
- Орієнтири складу (довідники університетських служб, відкривались у пошуку 2026-11-20, вміст сторінок повністю НЕ перевірявся): [Iowa State: Easy Low-Maintenance Houseplants](https://yardandgarden.extension.iastate.edu/how-to/easy-low-maintenance-houseplants); [Univ. of Maryland: Selecting Indoor Plants](https://extension.umd.edu/resource/selecting-indoor-plants); [Univ. of Nebraska–Lincoln, G837](https://extensionpubs.unl.edu/publication/g837/na/html/view); [Colorado State: popular houseplants for beginners](https://arapahoe.extension.colostate.edu/2024/01/05/popular-houseplants-for-beginners); [Oregon State: five easy houseplants](https://extension.oregonstate.edu/sites/default/files/documents/54611/five-easy-houseplants-worth-trying.pdf); університет Джорджії має перелік вимог ≈ 200 кімнатних рослин (на нього посилаються [Piedmont Master Gardeners](https://piedmontmastergardeners.org/?p=39979)). Це допоміжні джерела складу; повний список не копіювався з жодного з них.

## 2. Партії

Мінімум для релізу 1.1: B0–B5 (150 карток), B6–B7 у 1.2 (рішення Власника Q-1.1-6). ID нових рослин = латинський slug.

### B0: 20 наявних + 5 нових (переписати на новий формат, 4 мови)

| # | ID | Латинська назва | Українська назва | Група | Причина включення |
|---|---|---|---|---|---|
| 1 | `zamioculcas` (наявний) | *Zamioculcas zamiifolia* | Заміокулькас | наявні | наявна в застосунку (ID зберігається) |
| 2 | `sansevieria` (наявний) | *Dracaena trifasciata* | Сансевієрія | наявні | наявна в застосунку (ID зберігається) |
| 3 | `epipremnum` (наявний) | *Epipremnum aureum* | Епіпремнум золотистий | наявні | наявна в застосунку (ID зберігається) |
| 4 | `monstera` (наявний) | *Monstera deliciosa* | Монстера делікатесна | наявні | наявна в застосунку (ID зберігається) |
| 5 | `spathiphyllum` (наявний) | *Spathiphyllum wallisii* | Спатифілум | наявні | наявна в застосунку (ID зберігається) |
| 6 | `chlorophytum` (наявний) | *Chlorophytum comosum* | Хлорофітум чубатий | наявні | наявна в застосунку (ID зберігається) |
| 7 | `crassula` (наявний) | *Crassula ovata* | Красула (товстянка) | наявні | наявна в застосунку (ID зберігається) |
| 8 | `ficus_elastica` (наявний) | *Ficus elastica* | Фікус каучуконосний | наявні | наявна в застосунку (ID зберігається) |
| 9 | `ficus_benjamina` (наявний) | *Ficus benjamina* | Фікус Бенджаміна | наявні | наявна в застосунку (ID зберігається) |
| 10 | `dracaena_marginata` (наявний) | *Dracaena marginata* | Драцена облямована | наявні | наявна в застосунку (ID зберігається) |
| 11 | `peperomia` (наявний) | *Peperomia obtusifolia* | Пеперомія туполиста | наявні | наявна в застосунку (ID зберігається) |
| 12 | `aglaonema` (наявний) | *Aglaonema commutatum* | Аглаонема | наявні | наявна в застосунку (ID зберігається) |
| 13 | `philodendron` (наявний) | *Philodendron hederaceum* | Філодендрон лазячий | наявні | наявна в застосунку (ID зберігається) |
| 14 | `syngonium` (наявний) | *Syngonium podophyllum* | Сингоніум | наявні | наявна в застосунку (ID зберігається) |
| 15 | `aloe_vera` (наявний) | *Aloe vera* | Алое вера | наявні | наявна в застосунку (ID зберігається) |
| 16 | `tradescantia` (наявний) | *Tradescantia zebrina* | Традесканція зебрина | наявні | наявна в застосунку (ID зберігається) |
| 17 | `maranta` (наявний) | *Maranta leuconeura* | Маранта триколірна | наявні | наявна в застосунку (ID зберігається) |
| 18 | `schefflera` (наявний) | *Schefflera arboricola* | Шефлера деревоподібна | наявні | наявна в застосунку (ID зберігається) |
| 19 | `calathea` (наявний) | *Goeppertia makoyana* | Калатея Макоя | наявні | наявна в застосунку (ID зберігається) |
| 20 | `begonia` (наявний) | *Begonia maculata* | Бегонія плямиста | наявні | наявна в застосунку (ID зберігається) |
| 21 | `pilea_peperomioides` | *Pilea peperomioides* | Пілея пеперомієвидна | листяні | дуже популярна в соцмережах, проста, компактна |
| 22 | `phalaenopsis_hybrid` | *Phalaenopsis hybrid* | Фаленопсис (гібриди) | орхідеї | найпопулярніша кімнатна орхідея, часті запити про догляд |
| 23 | `ficus_lyrata` | *Ficus lyrata* | Фікус ліроподібний | дерева | модна велика рослина, часті питання про листопад |
| 24 | `hoya_carnosa` | *Hoya carnosa* | Хойя м'ясиста | ліани | популярна квітуча ліана, легка |
| 25 | `nephrolepis_exaltata` | *Nephrolepis exaltata* | Нефролепіс | папороті | найпоширеніша кімнатна папороть |

### B1: найпоширеніші листяні, дерева й ліани

| # | ID | Латинська назва | Українська назва | Група | Причина включення |
|---|---|---|---|---|---|
| 1 | `dracaena_fragrans` | *Dracaena fragrans* | Драцена запашна | дерева | дуже поширена, витримує тінь |
| 2 | `dracaena_reflexa` | *Dracaena reflexa* | Драцена загнута (пісня Індії) | дерева | поширена офісна рослина |
| 3 | `dracaena_sanderiana` | *Dracaena sanderiana* | Драцена Сандера (щасливий бамбук) | дерева | продається всюди, часто в воді |
| 4 | `dracaena_deremensis` | *Dracaena deremensis* | Драцена деремська | дерева | поширена, легка |
| 5 | `yucca_gigantea` | *Yucca gigantea* | Юка гігантська | дерева | дуже поширена, невибаглива |
| 6 | `pachira_aquatica` | *Pachira aquatica* | Пахіра водна (грошове дерево) | дерева | популярний подарунок |
| 7 | `beaucarnea_recurvata` | *Beaucarnea recurvata* | Бокарнея вигнута (пляшкова пальма) | дерева | популярна, толерантна до пересушування |
| 8 | `aspidistra_elatior` | *Aspidistra elatior* | Аспідистра висока | листяні | стійка до тіні, класика |
| 9 | `monstera_adansonii` | *Monstera adansonii* | Монстера Адансона | ліани | популярна ліана, часті запити |
| 10 | `scindapsus_pictus` | *Scindapsus pictus* | Сциндапсус строкатий | ліани | популярна, схожа на епіпремнум |
| 11 | `rhaphidophora_tetrasperma` | *Rhaphidophora tetrasperma* | Рафідофора чотирьохнасінна (міні-монстера) | ліани | популярна в соцмережах |
| 12 | `philodendron_bipinnatifidum` | *Philodendron bipinnatifidum* | Філодендрон перисторозсічений (ксанаду) | листяні | велика декоративна |
| 13 | `philodendron_melanochrysum` | *Philodendron melanochrysum* | Філодендрон чорне золото | ліани | колекційна, популярна |
| 14 | `dieffenbachia_seguine` | *Dieffenbachia seguine* | Діфенбахія | листяні | дуже поширена, токсична, потрібна обережність |
| 15 | `alocasia_amazonica` | *Alocasia amazonica* | Алоказія Амазонська | листяні | популярна, примхлива, багато питань |
| 16 | `anthurium_andraeanum` | *Anthurium andraeanum* | Антуріум Андре | квітучі | поширений подарунок, квітне |
| 17 | `hedera_helix` | *Hedera helix* | Плющ звичайний | ліани | класика, токсичний для тварин |
| 18 | `cissus_rhombifolia` | *Cissus rhombifolia* | Цисус ромболистий | ліани | легка, популярна |
| 19 | `asparagus_setaceus` | *Asparagus setaceus* | Аспарагус перистий | листяні | поширений |
| 20 | `asparagus_densiflorus` | *Asparagus densiflorus* | Аспарагус Шпренгера | листяні | поширений, токсичний |
| 21 | `fatsia_japonica` | *Fatsia japonica* | Фатсія японська | дерева | витримує прохолоду |
| 22 | `cordyline_fruticosa` | *Cordyline fruticosa* | Кордиліна | дерева | яскраве листя |
| 23 | `codiaeum_variegatum` | *Codiaeum variegatum* | Кротон (кодієум) | листяні | яскрава, примхлива |
| 24 | `fittonia_albivenis` | *Fittonia albivenis* | Фітонія | листяні | популярна міні-рослина |
| 25 | `hypoestes_phyllostachya` | *Hypoestes phyllostachya* | Гіпестес (рослина-веснянка) | листяні | поширена |

### B2: пальми, великі дерева, цитруси, фікуси

| # | ID | Латинська назва | Українська назва | Група | Причина включення |
|---|---|---|---|---|---|
| 1 | `howea_forsteriana` | *Howea forsteriana* | Ховея Форстера (кентія) | пальми | найпоширеніша кімнатна пальма |
| 2 | `chamaedorea_elegans` | *Chamaedorea elegans* | Хамедорея витончена | пальми | компактна, безпечна для тварин за джерелами |
| 3 | `dypsis_lutescens` | *Dypsis lutescens* | Діпсис жовтуватий (арека) | пальми | дуже поширена |
| 4 | `rhapis_excelsa` | *Rhapis excelsa* | Рапіс високий | пальми | стійка, повільна |
| 5 | `phoenix_roebelenii` | *Phoenix roebelenii* | Фінік Робелена | пальми | поширена декоративна |
| 6 | `livistona_chinensis` | *Livistona chinensis* | Ліністона китайська | пальми | поширена |
| 7 | `cycas_revoluta` | *Cycas revoluta* | Цикас повислий (саговник) | пальми | токсичний, потрібні застереження |
| 8 | `strelitzia_reginae` | *Strelitzia reginae* | Стрелітція королівська | дерева | популярна квітуча |
| 9 | `strelitzia_nicolai` | *Strelitzia nicolai* | Стрелітція Ніколая | дерева | велика декоративна |
| 10 | `musa_acuminata` | *Musa acuminata* | Банан кімнатний | дерева | часті запити |
| 11 | `ficus_microcarpa` | *Ficus microcarpa* | Фікус мікрокарпа (женьшень) | дерева | популярний бонсай-подібний |
| 12 | `ficus_pumila` | *Ficus pumila* | Фікус карликовий | ліани | поширений |
| 13 | `ficus_binnendijkii` | *Ficus binnendijkii* | Фікус Бінендійка (алі) | дерева | поширений |
| 14 | `ficus_benghalensis` | *Ficus benghalensis* | Фікус бенгальський | дерева | поширений |
| 15 | `schefflera_actinophylla` | *Schefflera actinophylla* | Шефлера промениста | дерева | поширена |
| 16 | `polyscias_fruticosa` | *Polyscias fruticosa* | Поліціас | дерева | декоративний |
| 17 | `radermachera_sinica` | *Radermachera sinica* | Радермахера китайська | дерева | поширена |
| 18 | `murraya_paniculata` | *Murraya paniculata* | Муррайя | дерева | квітуча, ароматна |
| 19 | `citrus_x_microcarpa` | *Citrus × microcarpa* | Каламондин (кімнатний цитрус) | дерева | дуже популярний, плодоносить |
| 20 | `coffea_arabica` | *Coffea arabica* | Кавове дерево | дерева | популярне |
| 21 | `tetrastigma_voinierianum` | *Tetrastigma voinierianum* | Тетрастигма | ліани | велика ліана |
| 22 | `cissus_antarctica` | *Cissus antarctica* | Цисус антарктичний | ліани | поширена |
| 23 | `nolina_recurvata` | *Nolina recurvata* | Нолина | дерева | схожа на бокарнею |
| 24 | `dracaena_surculosa` | *Dracaena surculosa* | Драцена крапчаста | дерева | поширена |
| 25 | `ravenea_rivularis` | *Ravenea rivularis* | Равенея | пальми | поширена |

### B3: сукуленти й молочаї

| # | ID | Латинська назва | Українська назва | Група | Причина включення |
|---|---|---|---|---|---|
| 1 | `echeveria_elegans` | *Echeveria elegans* | Ехеверія витончена | сукуленти | дуже популярна |
| 2 | `haworthiopsis_attenuata` | *Haworthiopsis attenuata* | Гавортія смугаста | сукуленти | дуже популярна |
| 3 | `haworthia_cooperi` | *Haworthia cooperi* | Гавортія Купера | сукуленти | популярна |
| 4 | `aloe_aristata` | *Aloe aristata* | Алое остисте | сукуленти | поширене |
| 5 | `aloe_arborescens` | *Aloe arborescens* | Алое деревоподібне | сукуленти | поширене |
| 6 | `kalanchoe_blossfeldiana` | *Kalanchoe blossfeldiana* | Каланхое Блосфельда | сукуленти | поширена квітуча |
| 7 | `kalanchoe_daigremontiana` | *Kalanchoe daigremontiana* | Каланхое Дегремона | сукуленти | поширена |
| 8 | `kalanchoe_tomentosa` | *Kalanchoe tomentosa* | Каланхое повстисте | сукуленти | популярна |
| 9 | `sedum_morganianum` | *Sedum morganianum* | Седум Моргана (ослячий хвіст) | сукуленти | популярна підвісна |
| 10 | `sedum_rubrotinctum` | *Sedum rubrotinctum* | Седум червонуватий | сукуленти | поширений |
| 11 | `sempervivum_tectorum` | *Sempervivum tectorum* | Молодило покрівельне | сукуленти | поширене |
| 12 | `lithops` | *Lithops* | Літопси | сукуленти | цікаві, складний полив |
| 13 | `graptopetalum_paraguayense` | *Graptopetalum paraguayense* | Граптопеталум | сукуленти | поширений |
| 14 | `curio_rowleyanus` | *Curio rowleyanus* | Кліняя Раулі (нитка перлів) | сукуленти | популярна підвісна |
| 15 | `curio_radicans` | *Curio radicans* | Кліня (банановий ланцюжок) | сукуленти | популярна підвісна |
| 16 | `portulacaria_afra` | *Portulacaria afra* | Портулакарія африканська | сукуленти | поширена |
| 17 | `aeonium_arboreum` | *Aeonium arboreum* | Еонієм деревоподібний | сукуленти | поширений |
| 18 | `crassula_perforata` | *Crassula perforata* | Красула пронизана | сукуленти | поширена |
| 19 | `crassula_arborescens` | *Crassula arborescens* | Красула деревоподібна | сукуленти | поширена |
| 20 | `ceropegia_woodii` | *Ceropegia woodii* | Церопегія Вуда | сукуленти | популярна підвісна |
| 21 | `hoya_kerrii` | *Hoya kerrii* | Хойя Керрі (серце) | сукуленти | подарунок на день закоханих |
| 22 | `euphorbia_trigona` | *Euphorbia trigona* | Молочай тригранний | сукуленти | поширений, токсичний сік |
| 23 | `euphorbia_milii` | *Euphorbia milii* | Молочай Мілля | сукуленти | поширений, токсичний сік |
| 24 | `euphorbia_pulcherrima` | *Euphorbia pulcherrima* | Пуансетія (різдвяна зірка) | квітучі | сезонно дуже популярна |
| 25 | `adenium_obesum` | *Adenium obesum* | Аденіум | сукуленти | квітуча, токсична |

### B4: квітучі

| # | ID | Латинська назва | Українська назва | Група | Причина включення |
|---|---|---|---|---|---|
| 1 | `saintpaulia_ionantha` | *Saintpaulia ionantha* | Сентполія (узамбарська фіалка) | квітучі | одна з найпопулярніших |
| 2 | `streptocarpus` | *Streptocarpus* | Стрептокарпус | квітучі | популярний |
| 3 | `sinningia_speciosa` | *Sinningia speciosa* | Глоксинія | квітучі | поширена |
| 4 | `begonia_rex` | *Begonia rex* | Бегонія королівська | листяні | поширена |
| 5 | `begonia_semperflorens` | *Begonia semperflorens* | Бегонія вічноквітуча | квітучі | поширена |
| 6 | `hibiscus_rosa_sinensis` | *Hibiscus rosa-sinensis* | Гібіскус китайський | квітучі | дуже популярний |
| 7 | `pelargonium_zonale` | *Pelargonium zonale* | Пеларгонія зональна (герань) | квітучі | дуже популярна |
| 8 | `cyclamen_persicum` | *Cyclamen persicum* | Цикламен персидський | квітучі | сезонний, токсичний |
| 9 | `clivia_miniata` | *Clivia miniata* | Клівія | квітучі | популярна |
| 10 | `hippeastrum_hybrid` | *Hippeastrum hybrid* | Гіппеаструм (амариліс) | квітучі | сезонно популярний, токсичний |
| 11 | `gardenia_jasminoides` | *Gardenia jasminoides* | Гарденія | квітучі | популярна, примхлива |
| 12 | `jasminum_polyanthum` | *Jasminum polyanthum* | Жасмин багатоквітковий | квітучі | поширений |
| 13 | `rhododendron_simsii` | *Rhododendron simsii* | Азалія кімнатна | квітучі | сезонна |
| 14 | `impatiens_walleriana` | *Impatiens walleriana* | Бальзамін Уоллера | квітучі | поширений |
| 15 | `abutilon` | *Abutilon* | Абутілон (кімнатний клен) | квітучі | поширений |
| 16 | `fuchsia_hybrida` | *Fuchsia hybrida* | Фуксія | квітучі | поширена |
| 17 | `bougainvillea_glabra` | *Bougainvillea glabra* | Бугенвілія | квітучі | популярна |
| 18 | `crossandra_infundibuliformis` | *Crossandra infundibuliformis* | Кроссандра | квітучі | поширена |
| 19 | `justicia_brandegeeana` | *Justicia brandegeeana* | Юстиція (креветкова рослина) | квітучі | поширена |
| 20 | `pachystachys_lutea` | *Pachystachys lutea* | Пахістахіс жовтий | квітучі | поширений |
| 21 | `aphelandra_squarrosa` | *Aphelandra squarrosa* | Афеландра | квітучі | поширена |
| 22 | `episcia_cupreata` | *Episcia cupreata* | Епісція | квітучі | популярна |
| 23 | `aeschynanthus_radicans` | *Aeschynanthus radicans* | Есхінантус | квітучі | підвісна, популярний |
| 24 | `schlumbergera_truncata` | *Schlumbergera truncata* | Шлюмбергера (декабрист) | квітучі | дуже популярна |
| 25 | `hatiora_gaertneri` | *Hatiora gaertneri* | Хатіора (великодній кактус) | квітучі | популярна |

### B5: марантові/калатеї, папороті, підвісні й дрібнолисті

| # | ID | Латинська назва | Українська назва | Група | Причина включення |
|---|---|---|---|---|---|
| 1 | `goeppertia_orbifolia` | *Goeppertia orbifolia* | Калатея круглолиста | марантові | дуже популярна, примхлива |
| 2 | `goeppertia_lancifolia` | *Goeppertia lancifolia* | Калатея списоподібна | марантові | поширена |
| 3 | `goeppertia_ornata` | *Goeppertia ornata* | Калатея прикрашена | марантові | поширена |
| 4 | `goeppertia_roseopicta` | *Goeppertia roseopicta* | Калатея рожево-фарбована | марантові | популярна |
| 5 | `goeppertia_zebrina` | *Goeppertia zebrina* | Калатея зебровидна | марантові | поширена |
| 6 | `ctenanthe_setosa` | *Ctenanthe setosa* | Ктенанта щетиниста | марантові | поширена |
| 7 | `stromanthe_sanguinea` | *Stromanthe sanguinea* | Строманта | марантові | поширена |
| 8 | `asplenium_nidus` | *Asplenium nidus* | Асплений гніздоподібний | папороті | дуже популярна |
| 9 | `adiantum_raddianum` | *Adiantum raddianum* | Адіантум Радді | папороті | популярна |
| 10 | `platycerium_bifurcatum` | *Platycerium bifurcatum* | Платицеріум (оленячі роги) | папороті | популярна колекційна |
| 11 | `pteris_cretica` | *Pteris cretica* | Птерис критський | папороті | поширена |
| 12 | `cyrtomium_falcatum` | *Cyrtomium falcatum* | Циртоміум | папороті | стійка |
| 13 | `blechnum_gibbum` | *Blechnum gibbum* | Блехнум | папороті | поширений |
| 14 | `davallia_canariensis` | *Davallia canariensis* | Давалія | папороті | поширена |
| 15 | `selaginella_kraussiana` | *Selaginella kraussiana* | Селагінелла | папороті | поширена |
| 16 | `tradescantia_spathacea` | *Tradescantia spathacea* | Традесканція човникоподібна | листяні | поширена |
| 17 | `tradescantia_pallida` | *Tradescantia pallida* | Традесканція блідо-фіолетова | листяні | поширена |
| 18 | `callisia_repens` | *Callisia repens* | Калісія повзуча | листяні | поширена |
| 19 | `plectranthus_scutellarioides` | *Plectranthus scutellarioides* | Колеус | листяні | поширений |
| 20 | `plectranthus_verticillatus` | *Plectranthus verticillatus* | Плектрантус (шведський плющ) | листяні | поширений |
| 21 | `gynura_aurantiaca` | *Gynura aurantiaca* | Гінура | листяні | поширена |
| 22 | `pilea_cadierei` | *Pilea cadierei* | Пілея Кадьє | листяні | поширена |
| 23 | `pilea_involucrata` | *Pilea involucrata* | Пілея закутана | листяні | поширена |
| 24 | `peperomia_argyreia` | *Peperomia argyreia* | Пеперомія срібляста (кавун) | листяні | дуже популярна |
| 25 | `peperomia_caperata` | *Peperomia caperata* | Пеперомія зморшкувата | листяні | поширена |

### B6: бромелієві, орхідеї, кактуси, дрібні (у 1.2)

| # | ID | Латинська назва | Українська назва | Група | Причина включення |
|---|---|---|---|---|---|
| 1 | `guzmania_lingulata` | *Guzmania lingulata* | Гузманія | бромелієві | дуже популярна |
| 2 | `aechmea_fasciata` | *Aechmea fasciata* | Ехмея смугаста | бромелієві | популярна |
| 3 | `vriesea_splendens` | *Vriesea splendens* | Вріезія блискуча | бромелієві | популярна |
| 4 | `tillandsia_ionantha` | *Tillandsia ionantha* | Тілландсія іонанта | бромелієві | повітряна рослина, популярна |
| 5 | `tillandsia_cyanea` | *Tillandsia cyanea* | Тілландсія синя | бромелієві | поширена |
| 6 | `neoregelia_carolinae` | *Neoregelia carolinae* | Неорегелія | бромелієві | поширена |
| 7 | `cymbidium_hybrid` | *Cymbidium hybrid* | Цимбідіум | орхідеї | популярний |
| 8 | `dendrobium_nobile` | *Dendrobium nobile* | Дендробіум шляхетний | орхідеї | популярний |
| 9 | `paphiopedilum` | *Paphiopedilum* | Пафіопедилум (венерин черевичок) | орхідеї | популярний |
| 10 | `oncidium_hybrid` | *Oncidium hybrid* | Онцидіум | орхідеї | поширений |
| 11 | `epiphyllum_oxypetalum` | *Epiphyllum oxypetalum* | Епіфілум | кактуси | популярний |
| 12 | `rhipsalis_baccifera` | *Rhipsalis baccifera* | Ріпсаліс | кактуси | популярний |
| 13 | `opuntia_microdasys` | *Opuntia microdasys* | Опунція дрібноглоста | кактуси | дуже поширена |
| 14 | `mammillaria_elongata` | *Mammillaria elongata* | Мамілярія подовжена | кактуси | поширена |
| 15 | `echinocactus_grusonii` | *Echinocactus grusonii* | Ехінокактус Грузона | кактуси | поширений |
| 16 | `gymnocalycium_mihanovichii` | *Gymnocalycium mihanovichii* | Гімнокаліціум Міхановича | кактуси | поширений |
| 17 | `cereus_peruvianus` | *Cereus peruvianus* | Цереус перуанський | кактуси | поширений |
| 18 | `echinopsis` | *Echinopsis* | Ехінопсис | кактуси | квітучий |
| 19 | `rebutia` | *Rebutia* | Ребуція | кактуси | квітуча |
| 20 | `cleistocactus_strausii` | *Cleistocactus strausii* | Клейстокактус Штрауса | кактуси | поширений |
| 21 | `oxalis_triangularis` | *Oxalis triangularis* | Оксаліс трикутний | листяні | популярний |
| 22 | `soleirolia_soleirolii` | *Soleirolia soleirolii* | Солейролія | листяні | поширена |
| 23 | `mimosa_pudica` | *Mimosa pudica* | Мімоза сором'язлива | листяні | цікава, популярна |
| 24 | `ardisia_crenata` | *Ardisia crenata* | Ардизія | дерева | плодоносить |
| 25 | `nertera_granadensis` | *Nertera granadensis* | Нертера | листяні | сезонна |

### B7: колекційні й рідші (у 1.2)

| # | ID | Латинська назва | Українська назва | Група | Причина включення |
|---|---|---|---|---|---|
| 1 | `anthurium_scherzerianum` | *Anthurium scherzerianum* | Антуріум Шерцера | квітучі | поширений |
| 2 | `anthurium_crystallinum` | *Anthurium crystallinum* | Антуріум кришталевий | листяні | колекційний |
| 3 | `alocasia_zebrina` | *Alocasia zebrina* | Алоказія зебрина | листяні | поширена |
| 4 | `alocasia_macrorrhiza` | *Alocasia macrorrhiza* | Алоказія великокоренева | листяні | поширена |
| 5 | `caladium_bicolor` | *Caladium bicolor* | Калядіум | листяні | сезонний |
| 6 | `colocasia_esculenta` | *Colocasia esculenta* | Колоказія | листяні | поширена |
| 7 | `homalomena_rubescens` | *Homalomena rubescens* | Гомаломена | листяні | поширена |
| 8 | `philodendron_brandtianum` | *Philodendron brandtianum* | Філодендрон Брандта | ліани | поширений |
| 9 | `philodendron_erubescens` | *Philodendron erubescens* | Філодендрон рум'яний | ліани | поширений |
| 10 | `zamia_furfuracea` | *Zamia furfuracea* | Замія | пальми | поширена, токсична |
| 11 | `aglaonema_modestum` | *Aglaonema modestum* | Аглаонема скромна | листяні | поширена |
| 12 | `aglaonema_crispum` | *Aglaonema crispum* | Аглаонема хвиляста | листяні | поширена |
| 13 | `monstera_obliqua` | *Monstera obliqua* | Монстера косолиста | ліани | колекційна |
| 14 | `epipremnum_pinnatum` | *Epipremnum pinnatum* | Епіпремнум перистий | ліани | поширений |
| 15 | `dionaea_muscipula` | *Dionaea muscipula* | Венерина мухоловка | листяні | цікава, складна |
| 16 | `nepenthes_hybrid` | *Nepenthes hybrid* | Непентес | листяні | цікавий |
| 17 | `gasteria` | *Gasteria* | Гастерія | сукуленти | поширена |
| 18 | `haworthia_retusa` | *Haworthia retusa* | Гавортія притуплена | сукуленти | поширена |
| 19 | `agave_americana` | *Agave americana* | Агава американська | сукуленти | поширена |
| 20 | `pachypodium_lamerei` | *Pachypodium lamerei* | Пахіподіум Ламера | сукуленти | поширений |
| 21 | `stapelia` | *Stapelia* | Стапелія | сукуленти | цікава |
| 22 | `euphorbia_tirucalli` | *Euphorbia tirucalli* | Молочай тирукаллі | сукуленти | поширений, токсичний сік |
| 23 | `senecio_articulatus` | *Senecio articulatus* | Сенецiо членистий | сукуленти | поширений |
| 24 | `pelargonium_peltatum` | *Pelargonium peltatum* | Пеларгонія плющелиста | квітучі | поширена |
| 25 | `dracaena_angolensis` | *Dracaena angolensis* | Драцена ангольська (сансевієрія циліндрична) | сукуленти | поширена, відрізняється від сансевієрії |

## 3. Що ще треба перевірити (не зроблено)

- Латинські назви й прийняті таксони (напр. *Goeppertia* замість *Calathea*, *Haworthiopsis*, *Curio*, *Dracaena trifasciata* замість *Sansevieria*) звірити з ботанічною базою (POWO/GBIF) до початку партії; у списку можливі застарілі синоніми.
- Для рослин з токсичним соком/частинами (діфенбахія, молочаї, цикас, замія, аденіум, плющ, цикламен, гіппеаструм) безпека — лише з джерел, інакше `UNKNOWN` (правила партій).
- Складні/колекційні види (непентес, венерина мухоловка, літопси, колекційні філодендрони) можна замінити на простіші на вимогу Власника без зміни порядку партій.

## 4. Що потрібно від Власника

Прочитати таблиці (≈ 5 хвилин) і відповісти: **погоджую / викреслити такі рослини / додати такі**. Партії B0+ не починаються до погодження.
