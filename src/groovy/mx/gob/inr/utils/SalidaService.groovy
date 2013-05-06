package mx.gob.inr.utils

import java.lang.reflect.Constructor
import mx.gob.inr.farmacia.SalidaFarmacia;

abstract class SalidaService<S extends Salida> {
	
	public final int PERFIL_FARMACIA = 8
	public final int PERFIL_CEYE  = 10
	
	public UtilService utilService
	public EntradaService entradaService
	
	protected entitySalida
	protected entitySalidaDetalle
	protected entityEntradaDetalle
	protected entityArticulo
	
	protected String almacen
	
	public SalidaService(entitySalida, entitySalidaDetalle,
		 entityEntradaDetalle, entityArticulo, almacen){
		this.entitySalida = entitySalida
		this.entitySalidaDetalle = entitySalidaDetalle	
		this.entityEntradaDetalle = entityEntradaDetalle
		this.entityArticulo = entityArticulo
		
		this.almacen = almacen		
	}
	
	def S guardar(S salida){				
		salida.save([validate:false,flush:true])
		return salida		
	}
	
	def String actualizar(S salida, Integer idSalidaUpdate){		
	
	 def salidaUpdate = entitySalida.get(idSalidaUpdate);
	 
	 if(salidaUpdate.numeroSalida != salida.numeroSalida){
		 //Aplicar Validacion
		 if (checkFolioSalida(salida.numeroSalida)){
			 return "Folio ya existe";
		 }
	 }
	 
	 /**Si cambio la fecha de salida
	  * hay que regresar existencias y vlver a insertar
	  *
	  * */
	 if(salidaUpdate.fechaSalida.compareTo(salida.fechaSalida) != 0){
		 
	 }
	 
	 salidaUpdate.properties = salida.properties;
	 salidaUpdate.save([validate:false,flush:true])
	 
	 return "Salida Actualizada";
		
	}
	
	def consultar(params){
		
		def fechas = utilService.fechasAnioActual()
				
		def salidaList = entitySalida.createCriteria().list(params){
			
			between("fechaSalida",fechas.fechaInicio,fechas.fechaFin)			
			order("numeroSalida","desc")
		}
		
		def salidaTotal = entitySalida.createCriteria().get{
			projections{
				count()
			}			
			between("fechaSalida",fechas.fechaInicio,fechas.fechaFin)
		}
		
		[salidaList:salidaList, salidaTotal:salidaTotal]
		
	}
	
	def cancelar(Long idSalida){		
	
		def salida =  entitySalida.get(idSalida);
		regresarExistencias(salida)
		
		salida.estadoSalida = 'C'
		salida.save([validate:false,flush:true])
	}
	
	def guardarDetalle(Long clave, Double solicitado, Double surtido, S salida ){
		
		def entradas = cargarEntradasDeSalida(clave, salida.fechaSalida, almacen, surtido)
		
		entradas.each(){
			
			def articulo = entityArticulo.get(clave)
			
			def salidaDetalle = entitySalidaDetalle.newInstance()
			
			salidaDetalle.salida = salida
			salidaDetalle.renglonSalida =consecutivoRenglon(salida)
			salidaDetalle.entrada = it.entrada
			salidaDetalle.renglonEntrada = it.renglonEntrada
			salidaDetalle.articulo = articulo
			salidaDetalle.cantidadPedida = solicitado
			salidaDetalle.cantidadSurtida = it.restarExistencia
			salidaDetalle.fechaCaducidad = it.fechaCaducidad
			salidaDetalle.noLote = it.noLote
			salidaDetalle.precioUnitario = articulo.movimientoProm
			salidaDetalle.presupuesto = null
			salidaDetalle.actividad = null
			salidaDetalle.entradaDetalle = entityEntradaDetalle.get (it.id)
			
			salidaDetalle.save([validate:false,flush:true])
			
			it.existencia -=  it.restarExistencia
		}
		
		
	}
	
	def actualizarDetalle(Long idSalida, Long clave,Double solicitado, Double surtido){	
		
		borrarDetalle(idSalida, clave)
		guardarDetalle(clave,solicitado,surtido, entitySalida.get(idSalida))
		
	}
	
	def consultarDetalle(params){
		def sortIndex = params.sidx ?: 'name'
		def sortOrder  = params.sord ?: 'asc'
		def maxRows = Integer.valueOf(params.rows)
		def currentPage = Integer.valueOf(params.page) ?: 1
		def rowOffset = currentPage == 1 ? 0 : (currentPage - 1) * maxRows
		def idSalida  = Long.parseLong(params.idSalida)
		
		log.info("IDSALIDA " + params.idSalida)
		
		def entitySalidaDetalleName = entitySalidaDetalle.name
		
		def query =
		"""\
			select sd.articulo.id, sd.articulo.desArticulo, sd.articulo.unidad, sd.articulo.movimientoProm,
			sd.cantidadPedida,sum(sd.cantidadSurtida),sd.salida.fechaSalida
			from $entitySalidaDetalleName sd 			
			where sd.salida.id = $idSalida and sd.salida.almacen = '$almacen' 
			group by sd.articulo.id, sd.articulo.desArticulo, sd.articulo.unidad, sd.articulo.movimientoProm,
			sd.cantidadPedida,sd.salida.fechaSalida 
			order by sd.articulo.id

		"""
		
		def detalleCount = entitySalidaDetalle.executeQuery(query,[])
				
		def detalle = entitySalidaDetalle.executeQuery(query,[],[max: maxRows, offset: rowOffset])
		
		def totalRows = detalleCount.size();
		def numberOfPages = Math.ceil(totalRows / maxRows)
			
		def results = detalle?.collect {
			[
				 cell:[it[0], it[1]?.trim(),
					 it[2]?.trim(), it[3],
					  entradaService.disponibilidadArticulo(it[0], it[6],almacen),
					  it[4],it[5]], id: it[0]
			]
		}
		
		def jsonData = [rows: results, page: currentPage, records: totalRows, total: numberOfPages]
		
		return jsonData
		
	}
		
	def borrarDetalle(Long idSalida, Long clave){
		
		def detalle = entitySalidaDetalle.createCriteria().list(){
			eq('salida.id', idSalida)
			eq("articulo.id", clave)
		}
		
		detalle.each(){
			def entradaDetalle  = it.entradaDetalle
			
			if(entradaDetalle)
				entradaDetalle.existencia += it.cantidadSurtida
			
			it.delete([flush:true])
		}
		
	}
		
	private def regresarExistencias(S salida){
		
		def salidasDetalle = entitySalidaDetalle.createCriteria().list(){
			eq("salida", salida)
		}
		
		salidasDetalle.each(){
			def entradaDetalle  = it.entradaDetalle
			
			if(entradaDetalle){
				entradaDetalle.existencia += it.cantidadSurtida
				entradaDetalle.save([validate:false,flush:true])
			}
		}
		
	}
	
	private def cargarEntradasDeSalida(Long clave, Date fecha, String almacen, Double surtido){
		
		def entradas = entradaService.entradasDetalle(clave, fecha, almacen)
				
		log.info("Num entradas " + entradas.size())
		
		def entradasAfectadas = afectarEntradas(entradas, surtido);
		
		log.info("Num entradas2 " + entradasAfectadas.size())
		
		if(entradasAfectadas.size() == 0){
			
			//def entradaDetalle = new entityEntradaDetalle();	
			def entradaDetalle = entityEntradaDetalle.newInstance();
			
			entradaDetalle.entrada = null
			entradaDetalle.articulo = entityArticulo.get(clave)
			entradaDetalle.cantidad = 0.0
			entradaDetalle.existencia = 0.0
			entradaDetalle.precioEntrada = 0.0
			entradaDetalle.fechaCaducidad = null
			entradaDetalle.noLote = null
			entradaDetalle.restarExistencia = 0.0		

			entradasAfectadas.add(entradaDetalle);
		}
		
		entradasAfectadas
	}
		
	private def afectarEntradas(entradas, surtido){
		def despachado = 0.0;
		
		def result = [];
		
		for(entradaDetalle in entradas){
			despachado = entradaDetalle.existencia - surtido;
			
			log.info("Despachado " + despachado)
			
			if(despachado < 0){
				entradaDetalle.restarExistencia = entradaDetalle.existencia
				result.add(entradaDetalle)
				surtido = Math.abs(despachado)
			}
			else if(despachado >=0){
				entradaDetalle.restarExistencia = surtido
				result.add(entradaDetalle)
				break
			}
			
		}
		
		return result
	}
	
	
	def consecutivoRenglon(S salida){
		utilService.consecutivoRenglon(entitySalidaDetalle,"renglonSalida","salida",salida)
	}
	
	def consecutivoNumeroSalida(){
		utilService.consecutivoNumero(entitySalida, "numeroSalida","fechaSalida")
	}
	
	def checkFolioSalida(Integer folioSalida){
		
		utilService.checkFolio(entitySalida,"numeroSalida","fechaSalida", folioSalida)
		
	}
	
	def usuarios(Integer idPerfil){
		return utilService.usuarios(idPerfil)
		
	}
	
}
