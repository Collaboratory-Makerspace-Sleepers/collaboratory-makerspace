package com.makerspace.backend.services;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Builds the signed-waiver PDF that is emailed to the member. */
@Service
public class WaiverPdfService {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a z");

    public byte[] build(String memberName, String memberEmail, String equipmentName,
                        String signedName, ZonedDateTime signedAt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 54, 54, 54, 54);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font script = FontFactory.getFont(FontFactory.TIMES_ITALIC, 26);

            Paragraph t = new Paragraph("Makerspace Equipment Waiver", title);
            t.setAlignment(Element.ALIGN_CENTER);
            t.setSpacingAfter(16);
            doc.add(t);

            doc.add(new Paragraph("Member: " + memberName + " (" + memberEmail + ")", body));
            doc.add(new Paragraph("Equipment / class: " + equipmentName, body));
            Paragraph intro = new Paragraph(
                    "By signing this waiver, the member acknowledged and agreed to the following:", body);
            intro.setSpacingBefore(12);
            intro.setSpacingAfter(8);
            doc.add(intro);

            for (String[] section : WAIVER_SECTIONS) {
                Paragraph p = new Paragraph();
                p.add(new com.lowagie.text.Chunk(section[0] + ". ", heading));
                p.add(new com.lowagie.text.Chunk(section[1], body));
                p.setSpacingAfter(6);
                doc.add(p);
            }

            Paragraph sigLabel = new Paragraph("Signed with RabbitSign", heading);
            sigLabel.setSpacingBefore(18);
            doc.add(sigLabel);
            doc.add(new Paragraph(signedName, script));
            doc.add(new Paragraph("Signed on "
                    + WHEN.format(signedAt.withZoneSameInstant(ZoneId.systemDefault())), body));
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build waiver PDF", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }

    // Keep in sync with frontend/src/components/dashboard/waiverSections.js
    private static final String[][] WAIVER_SECTIONS = {
        {"Acknowledgment of Risk", "I understand that operating or being near makerspace equipment involves inherent risks, including but not limited to burns, fire hazards, exposure to fumes, eye injury, and damage to personal property. I voluntarily assume all risks associated with the use of the makerspace and its equipment."},
        {"Training & Proper Use", "I confirm that I have received proper training or instruction on the safe use of the equipment I intend to use, or I agree to seek assistance before operating any machinery. I will follow all posted rules, safety guidelines, and instructions provided by staff."},
        {"Personal Responsibility", "I agree to use all equipment responsibly and only for its intended purpose. I will not operate machinery under the influence of drugs or alcohol, and I will wear required safety equipment at all times."},
        {"Materials & Safety Compliance", "I understand that only approved materials may be used with makerspace equipment. I will not use hazardous, flammable, or prohibited materials and will confirm material safety if unsure."},
        {"Supervision & Access", "I understand that access to equipment may be restricted and that supervision may be required. I agree to comply with all access policies."},
        {"Release of Liability", "I hereby release, waive, and discharge Collaboratory Makerspace, its staff, affiliates, and representatives from any and all liability, claims, demands, or causes of action arising out of or related to any loss, damage, or injury, including serious injury or death, that may occur while using the facility or equipment."},
        {"Indemnification", "I agree to indemnify and hold harmless Collaboratory Makerspace from any claims resulting from my actions, misuse of equipment, or violation of safety rules."},
        {"Medical Treatment Authorization", "In the event of an emergency, I authorize staff to obtain medical treatment on my behalf if I am unable to do so."},
        {"Agreement & Understanding", "I have read this waiver, fully understand its terms, and sign it freely and voluntarily. I understand that I am giving up certain legal rights."}
    };
}
