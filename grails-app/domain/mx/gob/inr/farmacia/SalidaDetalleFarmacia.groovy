package mx.gob.inr.farmacia

import java.io.Serializable;
import java.util.Date;

import mx.gob.inr.utils.domain.SalidaDetalle;

class SalidaDetalleFarmacia extends SalidaDetalle {
	
	static auditable = true
	
	SalidaFarmacia salida
	//EntradaFarmacia entrada
	ArticuloFarmacia articulo
	EntradaDetalleFarmacia entradaDetalle	
	
	static belongsTo = [salida:SalidaFarmacia]
	
	static constraints = {
		fechaCaducidad(nullable:true)
		noLote(nullable:true)
		precioUnitario(nullable:true)
		actividad(nullable:true)
		presupuesto(nullable:true)
		importe(nullable:true)
	}
	
	static mapping = {
		version false
		table 'salida_detalle'
		salida column:'id_salida'
		articulo column :'cve_art'
		renglon column: 'renglon_salida'
		//entradaDetalle column:['id_entrada','renglon_entrada']
		entradaDetalle column:'id_entradadetalle'
		//entrada column:'id_entrada'		
		id column:'id_salidadetalle'
		id generator:'sequence' ,params:[sequence:'sq_idsalidadetallefarmacia']
		
		importe formula: "cantidad_surtida * precio_unitario"
	}    
}
