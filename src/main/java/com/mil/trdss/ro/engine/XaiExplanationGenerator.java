package com.mil.trdss.ro.engine;

import com.mil.trdss.ro.domain.dto.TacticalRecommendationDTO;
import com.mil.trdss.ro.domain.dto.TargetIntakeDTO;
import com.mil.trdss.ro.domain.enums.TargetMovementStatus;
import com.mil.trdss.ro.domain.enums.WeatherCondition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class XaiExplanationGenerator {

    public String generate(
            TargetIntakeDTO targetIntake,
            List<ExclusionEngine.ExclusionReason> excludedAssets,
            TacticalScoringEngine.ScoringOutcome scoringOutcome) {

        List<TacticalRecommendationDTO.RankedModelGroup> rankedModelGroups = scoringOutcome.rankedModelGroups();
        StringBuilder explanation = new StringBuilder();

        explanation.append(String.format(
                "Hedef %s (Tehdit Seviyesi: %d/10) için taktik değerlendirme tamamlandı. ",
                targetIntake.target().targetId(), targetIntake.target().threatLevel()));

        if (targetIntake.target().weatherContext() == WeatherCondition.DENSE_FOG) {
            explanation.append("Yoğun sis nedeniyle lazer güdümlü mühimmatlar (MAM-L, MAM-C, BOZOK) düşük öncelikli değerlendirildi; ")
                    .append("GPS/INS güdümlü sistemler (TOLUN, SOM) öncelik kazandı. ");
        }

        if (targetIntake.ewContext().activeEwThreat() && !targetIntake.ewContext().jammerPolygon().isEmpty()) {
            explanation.append("Aktif Elektronik Harp (EW) tehdidi tespit edildi; rota, tespit edilen karıştırma (jammer) bölgesini bypass edecek şekilde uzatıldı. ");
        }

        if (targetIntake.target().movementStatus() == TargetMovementStatus.MOVING) {
            explanation.append("Hedefin hareketli (MOVING) olması sebebiyle anlık takip yapabilen lazer güdümlü mühimmatlar (MAM-L, MAM-C, BOZOK) önceliklendirilmiş; ")
                    .append("sabit koordinata taarruz eden GPS/INS güdümlü sistemlere (TOLUN, SOM) düşük öncelik verilmiştir. ");
        } else {
            explanation.append("Hedefin sabit (STATIONARY) olması sebebiyle GPS/INS güdümlü sistemler (TOLUN, SOM) menzil dışı (standoff) hassas taarruz için avantajlı değerlendirilmiş; ")
                    .append("lazer tanımlama riski taşımadan temel öncelik seviyesi korunmuştur. ");
        }

        if (rankedModelGroups.isEmpty()) {
            explanation.append("Uygun hiçbir varlık bulunamadı; tüm envanter dışlama kriterlerine takıldı.");
        } else {
            TacticalRecommendationDTO.RankedModelGroup topGroup = rankedModelGroups.get(0);
            explanation.append(String.format(
                    "En yüksek öncelikli öneri: %s modeli (%d adet müsait varlık). ",
                    topGroup.modelName(), topGroup.totalAvailableCount()));

            if (scoringOutcome.topGroupSwarmApplied()) {
                explanation.append("Tek başına üstünlük (Overmatch) sağlayamayan orta seviye mühimmatlar için Sürü (Swarm) Tahsisi uygulandı; ")
                        .append("matematiksel üstünlük 2 varlığın birleştirilmesiyle sağlandı. ");
            } else if (scoringOutcome.topGroupOvermatchAchieved()) {
                explanation.append("Overmatch Doktrini gereği, seçilen mühimmat gücü hedef tehdit seviyesini tek başına aşmaktadır. ");
            } else {
                explanation.append("UYARI: Mevcut envanterdeki hiçbir varlık veya küme, hedef tehdit seviyesini karşılayacak mühimmat gücüne veya ")
                        .append("Sürü Tahsisi için gereken orta seviye varlık sayısına ulaşamamaktadır; bu, yalnızca mevcut en iyi seçenektir ve operatör onayı gerektirir. ");
            }

            if (topGroup.tieBreakerApplied()) {
                explanation.append("Eşit puanlı varlıklar arasında, önce yakıt yüzdesi yüksekliği, ardından varlık kimliğinin (assetId) ")
                        .append("alfanumerik artan sıralaması esas alınarak deterministik seçim yapıldı. ");
            }
        }

        if (!excludedAssets.isEmpty()) {
            String reasonSummary = excludedAssets.stream()
                    .map(reason -> String.format("%s (%s)", reason.assetId(), translateReason(reason.reasonCode())))
                    .collect(Collectors.joining(", "));
            explanation.append(String.format(
                    "%d varlık aşağıdaki nedenlerle değerlendirme dışı bırakıldı: %s.",
                    excludedAssets.size(), reasonSummary));
        }

        return explanation.toString();
    }

    private String translateReason(String reasonCode) {
        return switch (reasonCode) {
            case ExclusionEngine.REASON_MAINTENANCE_REQUIRED -> "Bakım Gerekiyor";
            case ExclusionEngine.REASON_MANUAL_OVERRIDE -> "Manuel Kontrol Altında";
            case ExclusionEngine.REASON_ZERO_MUNITION_COUNT -> "Mühimmat Tükendi";
            case ExclusionEngine.REASON_BELOW_BINGO_FUEL_THRESHOLD -> "Bingo Yakıt Eşiğinin Altında";
            default -> reasonCode;
        };
    }
}
