package mx.gob.inr.ceye

import mx.gob.inr.utils.Cie09
import mx.gob.inr.utils.Salida

class SalidaCeye extends Salida {

    CatAreaCeye area
	Cie09 diagnostico
	Short nosala
	String paqueteq
	
	static hasMany = [salidasDetalle:SalidaDetalleCeye]
	
	static constraints = {
	
	}

	static mapping = {
		table 'salida_ceye'
		id column:'id_salida'		
		entrego column:'entrego'		
		usuario column:'id_usuario'
		paciente column:'id_paciente'
		diagnostico column:'id_diagnostico'
		area column:'cve_area'
		id generator:'sequence' ,params:[sequence:'sq_idsalida_ceye']
		version false
	}
}
